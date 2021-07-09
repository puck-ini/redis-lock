# Redis 分布式锁



## 什么是分布式锁

单机情况下，当多线程操作共享变量时，为了保证数据的一致性，同一时间只能有一个线程在操作该变量，这时候就得上锁。

当在集群这种多机的情况时，单机上的锁无法被其他机器上的线程获取，也就是说不能保证在同一时间只能有一个线程操作该变量，这个时候就得引入一个中间层，保证多机情况下每一个线程都能访问到锁，这就是分布式锁。



## 分布式锁实现

分布式锁的实现可以有很多种，例如关系型数据库，Zookeeper，分布式缓存等。

本文主要介绍分布式缓存 Redis 的分布式锁实现。



## Redis 分布式锁实现

锁要保证互斥性，分布式锁也不例外，Redis 中可以使用键值对保存锁，在同一时间确保只能让一个客户端获取到锁。



### set

保证互斥性可以直接使用 set 命令，因为 Redis 是单线程处理命令，所以不会造成多个客户端同时获取到锁。首先约定好 key 值，这样所有客户端都能访问到锁，value 值为0表示未获取到锁，1表示获取到锁。当有客户端试图获取锁时先判断值是否为0，为0则将 value 设置为1表示已有客户端获取到锁，其他客户端在获取锁时就无法获取成功。

**存在的问题：**

虽然 Redis 单线程处理命令可以保证只有一个客户端获取到锁，但是客户端方面却无法保证，因为客户端获取锁需要先判断值是否为0 ，之后才会设置锁的值为1，这两个操作无法保证获取锁的原子性。



### setnx

setnx 的意思是 set if not exist。这个操作在 Redis 2.6.12 版本及之后支持，该命令会在设置键值对时判断库中是否存在，如果不存在就会创建键值对，设置成功会返回1，失败会返回0，这样判断和加锁的操作就可以直接使用 setnx 命令。

**存在问题：**

如果获取到锁的客户端崩溃了就会导致锁一直不能被释放，其他客户端也无法加锁。



### set nx px

根据上述问题可以使用 set nx px 命令（2.6.12），该命令在保证 setnx 的功能同时可以对 key 设置过时间。这样获取到锁的客户端崩溃了也不会一直持有锁导致其他客户端无法获取锁。

**存在问题：**

设置了过期时间之后锁可能会被误删，例如客户端 A 获取到锁之后处理业务时间超时导致锁过期了，这个时候客户端 B 获取到锁还没进行业务处理时，客户端 A 刚好处理完将锁删了就会导致客户端 B 获取到的锁被误删。



### 唯一值

为了防止锁被误删，可以将锁的 value 设置成客户端的唯一标识，这样在删除锁是先判断唯一值是否是自己上的锁，即谁上的锁谁才能删除。

**存在问题：**

原本的删除锁只是一个操作，是原子性的，但是这次多了判断之后就会导致删除锁也变成不是原子性的。



### Lua 脚本

Lua 是一种轻量小巧的脚本语言，用标准C语言编写并以源代码形式开放， 其设计目的是为了嵌入应用程序中，从而为应用程序提供灵活的扩展和定制功能。

Redis 在执行 lua 脚本时是原子性的，不会被其他请求打断。

获取锁：

```
"if redis.call('setnx',KEYS[1],ARGV[1])==1 then\n" +
"    if redis.call('get',KEYS[1])==ARGV[1] then\n" +
"        return redis.call('expire',KEYS[1],ARGV[2])\n" +
"    else\n" +
"        return 0\n" +
"    end\n" +
"else\n" +
"end";
```

删除锁：

```
// 释放锁 比较value是否相等，避免误释放
"if redis.call(\"get\",KEYS[1]) == ARGV[1] then\n" +
"    return redis.call(\"del\",KEYS[1])\n" +
"else\n" +
"    return 0\n" +
"end";
```

**存在问题：**

锁不可重入，不支持阻塞等待，业务处理时间大于锁过期时间无法续期。



### 重入锁

可重入锁的最大作用是防止死锁。重入多少次就得释放多少次

不可重入锁例子。test1() 重复加锁会导致死锁。

```java
public class SpinLock {
    private AtomicReference<Thread> owner =new AtomicReference<>();

    /**
     * 将当前线程设置成锁
     */
    public void lock() {
        Thread current = Thread.currentThread();
        while(!owner.compareAndSet(null, current)) {
        }
    }

    /**
     * 释放锁
     */
    public void unlock () {
        Thread current = Thread.currentThread();
        owner.compareAndSet(current, null);
    }

    /**
     * SpinLock 是不可重入锁， 该方法会重复加锁会产生死锁
     */
    public void test1() {
        lock();
        try {
            System.out.println("test1");
            // test2 方法也使用了 SpinLock 加锁
            test2();
        }finally {
            unlock();
        }
    }

    public void test2() {
        lock();
        try {
            System.out.println("test2");
        }finally {
            unlock();
        }
    }

    public static void main(String[] args) {
        SpinLock spinLock = new SpinLock();
        spinLock.test1();
    }
}
```



解决不可重入锁可以增加一个计数，在重复加锁时计数加1，释放锁时计数键1

```java
public class SpinLock1 {

    private AtomicReference<Thread> owner =new AtomicReference<>();

    private int count = 0;

    public void lock() {
        Thread current = Thread.currentThread();
        if (current==owner.get()) {
            count++;
            return;
        }
        while(!owner.compareAndSet(null, current)) {
        }
    }
    
    public void unlock () {
        Thread current = Thread.currentThread();
        if(current==owner.get()){
            if (count!=0) {
                count--;
            } else {
                owner.compareAndSet(current, null);
            }

        }
    }

    public void test1() {
        lock();
        try {
            System.out.println("test1");
            test2();
        }finally {
            unlock();
        }
    }

    public void test2() {
        lock();
        try {
            System.out.println("test2");
        }finally {
            unlock();
        }
    }

    public static void main(String[] args) {
        SpinLock1 spinLock1 = new SpinLock1();
        spinLock1.test1();
    }
}
```



### 阻塞等待

可以在一个循环中不停的获取锁，但是这样效率会很低。



### 定时任务

解决业务处理时间大于锁过期时间的问题可以设置比较大的锁过期时间，但是这样无法做到将问题根本解决。这个时候可以使用一个定时任务在锁快过期时为锁重新设置过期时间。



### Redisson

> Redisson是一个在Redis的基础上实现的Java驻内存数据网格（In-Memory Data Grid）。它不仅提供了一系列的分布式的Java常用对象，还提供了许多分布式服务。其中包括(BitSet, Set, Multimap, SortedSet, Map, List, Queue, BlockingQueue, Deque, BlockingDeque, Semaphore, Lock, AtomicLong, CountDownLatch, Publish / Subscribe, Bloom filter, Remote service, Spring cache, Executor service, Live Object service, Scheduler service) Redisson提供了使用Redis的最简单和最便捷的方法。Redisson的宗旨是促进使用者对Redis的关注分离（Separation of Concern），从而让使用者能够将精力更集中地放在处理业务逻辑上。

简而言之 Redisson 是一个 Java 实现的操作 Redis 的客户端。使用 Redisson 提供的分布式锁功能可以避免上述所说的所有问题。



#### 唯一值

使用的是UUID + 当前线程Id

![image-20210121150056729](img/redisfbss/image-20210121150056729.png)



#### 加锁 lua 脚本。

Redisson 实现的分布式锁使用的是 Redis hash 数据类型，key 保存锁名，field 保存客户端的唯一值，value 表示重入次数。

> 如果锁不存在设置锁，同时设置锁的过期时间（internalLockLeaseTime），return
>
> 如果锁存在，判断是否属于自己加的锁，如果是则将值加1，同时将重新设置过期时间（重入锁）。
>
> 如果锁存在并且不属于自己加的锁则返回锁目前剩余的过期时间。

![image-20210121150034511](img/redisfbss/image-20210121150034511.png)

#### 解锁 lua 脚本。

> 如果锁不存在，publish 发送锁已经解锁的消息。
>
> 如果锁存在，判断是否属于自己创建的锁，不属于自己的锁直接返回不处理。
>
> 如果属于自己的锁，将值减1并返回，判断该值如果大于0，重新设置锁的过期时间，否则删除该锁并发布锁已经解锁的消息。

![image-20210121145836082](img/redisfbss/image-20210121145836082.png)



可以看到解锁脚本中有一个发布事件的处理，这个发布事件的作用主要是通知其他正在阻塞获取锁的客户端锁已经被释放，可以再次获取锁。



#### 阻塞等待

Redisson 的阻塞等待使用了 Redis 的 pub/sub 机制（发布订阅机制）。当客户端未获取到锁时会订阅一个频道等待获取锁的客户端释放锁时发布释放锁事件。

![image-20210412135634196](img/redisfbss/image-20210412135634196.png)

#### 锁续期。

续期只有在获取锁时不设置等待时间和过期时间才会自动续期，当客户端获取到锁时会开启一个定时任务续期。Redisson 锁默认情况下锁的过期时间为30秒，每10秒给锁续期的时间也为30秒。

![image-20210407153223447](img/redisfbss/image-20210407153223447.png)

## RedLock（多节点分布式锁）

上述的分布式锁都是在单机情况下的锁，多节点分布式锁是为了保证锁的高可用而存在的。

Redlock 是 Redis 作者 antirez 提出的算法，在 Redis 的分布式环境中，我们假设有N个Redis master。这些节点**完全互相独立，不存在主从复制或者其他集群协调机制。当且仅当从大多数（N/2+1）的 Redis 节点都取到锁，并且使用的时间小于锁失效时间时，锁才算获取成功**。



> Redlock 的方案不再需要部署从库和哨兵实例，只部署主库，但主库要部署多个，官方推荐至少 5 个实例。



### RedLock 流程

1. 客户端先获取「当前时间戳T1」
2. 客户端依次向这 5 个 Redis 实例发起加锁请求（用前面讲到的 SET 命令），且每个请求会设置超时时间（毫秒级，要远小于锁的有效时间），如果某一个实例加锁失败（包括网络超时、锁被其它人持有等各种异常情况），就立即向下一个 Redis 实例申请加锁
3. 如果客户端从 >=3 个（大多数，N / 2 + 1）以上 Redis 实例加锁成功，则再次获取「当前时间戳T2」，如果 T2 - T1 < 锁的过期时间，此时，认为客户端加锁成功，否则认为加锁失败
4. 加锁成功，去操作共享资源（例如修改 MySQL 某一行，或发起一个 API 请求）
5. 加锁失败，向「全部节点」发起释放锁请求（前面讲到的 Lua 脚本释放锁）



### RedLock 安全吗？

见参考文章。

我们首先来看看分布式系统常见的问题：

- N：Network Delay，网络延迟

TCP 虽然保证了传输的可靠性，但是网络延迟是无法避免的

- P：Process Pause，进程暂停

例如 Java 中的 GC （垃圾回收）

- C：Clock Drift，时钟漂移

使用 NTP 协议将本地设备时间与专门的时间服务器对其，造成的结果是设备的本地时间突然向前或向后跳跃。



知道了常见问题之后再来看看 RedLock 是如何处理的。







