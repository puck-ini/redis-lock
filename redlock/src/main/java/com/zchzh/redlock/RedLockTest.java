package com.zchzh.redlock;

import org.redisson.Redisson;
import org.redisson.RedissonRedLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.concurrent.TimeUnit;

/**
 * @author zengchzh
 * @date 2021/1/21
 */
public class RedLockTest {

    /**
     * redission 使用的是 hash 存储分布式锁 hash 中的属性名为 "UUID:threadId" 值为 threadId
     */
    public static void test(){
        Config config1 = new Config();
        config1.useSingleServer().setAddress("127.0.0.1:6379");
        RedissonClient redissonClient1 = Redisson.create(config1);

//        Config config2 = new Config();
//        config2.useSingleServer().setAddress("127.0.0.1:6379");
//        RedissonClient redissonClient2 = Redisson.create(config2);
//
//        Config config3 = new Config();
//        config3.useSingleServer().setAddress("127.0.0.1:6379");
//        RedissonClient redissonClient3 = Redisson.create(config3);

        String lockName = "redis_lock";

        // 对每一个redis 连接赋予一个 UUID
        RLock lock1 = redissonClient1.getLock(lockName);
//        RLock lock2 = redissonClient2.getLock(lockName);
//        RLock lock3 = redissonClient3.getLock(lockName);

//        RedissonRedLock redLock = new RedissonRedLock(lock1,lock2,lock3);
        System.out.println("redlock");
        RedissonRedLock redLock = new RedissonRedLock(lock1);
        System.out.println("redlock end");
        boolean isLock;
        try {
            System.out.println("start");
            // 500ms拿不到锁, 就认为获取锁失败。10000ms即10s是锁失效时间。trylock 分布式锁设置的唯一值为 UUID:threadId
            // 不设置锁失效时间则该锁为重入锁，默认失效时间为30s, 同时锁重入时设置的默认失效时间为30s
            // 将阻塞时间设置长才可以在别的客户端释放锁是申请到锁
//            isLock = redLock.tryLock(500, 10000, TimeUnit.MILLISECONDS);
            isLock = redLock.tryLock(60000,  TimeUnit.MILLISECONDS);
            if (isLock) {
                // do something
                for (int i = 0; i < 5; i++) {
                    Thread.sleep(10000);
                    System.out.println("test1");
                }
            }
            System.out.println("do end");
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            // 释放锁
            System.out.println("unlock");
            redLock.unlock();
            System.out.println("unlock end");
        }
    }

    public static void main(String[] args) {
        test();
    }
}