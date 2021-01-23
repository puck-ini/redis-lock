package com.zchzh.b;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author zengchzh
 * @date 2021/1/21
 */

@Component
public class RedisLockUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_LOCK_KEY = "redis_lock";

    private static final String REDIS_NOT_LOCK = "0";

    private static final String REDIS_LOCKED = "1";

    /**
     * 获取单机锁
     * @return
     */
    public boolean getLockV1(){
        String lock = (String) redisTemplate.opsForValue().get(REDIS_LOCK_KEY);
        if (StringUtils.isEmpty(lock) || Objects.equals(REDIS_NOT_LOCK, lock)){
            redisTemplate.opsForValue().getAndSet(REDIS_LOCK_KEY, REDIS_LOCKED);
            return true;
        }
        return false;
    }

    /**
     * 释放单机锁
     * @return
     */
    public void releaseLockV1(){
        redisTemplate.opsForValue().getAndSet(REDIS_LOCK_KEY, REDIS_NOT_LOCK);
    }


    /**
     * 分布式锁使用 setnx
     * @return
     */
    public Boolean getLockV2(){
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(REDIS_LOCK_KEY, REDIS_LOCKED);
        if (flag == null){
            return false;
        }
        return flag;
    }

    /**
     * 释放分布式锁
     * @return
     */
    public void releaseLockV2(){
        redisTemplate.delete(REDIS_LOCK_KEY);
    }

    /**
     * 分布式锁使用 setnx, 同时设置过期时间
     * @return
     */
    public Boolean getLockV3(){
        Boolean flag = redisTemplate.opsForValue()
                // 设置 10s 过期
                .setIfAbsent(REDIS_LOCK_KEY, REDIS_LOCKED, 10, TimeUnit.SECONDS);
        if (flag == null){
            return false;
        }
        return flag;
    }

    /**
     * 释放分布式锁
     * @return
     */
    public void releaseLockV3(){
        redisTemplate.delete(REDIS_LOCK_KEY);
    }

    /**
     * 分布式锁使用 setnx, 同时设置过期时间, 将值设置为当前项目唯一值
     * @param uniqueValue
     * @return
     */
    public Boolean getLockV4(String uniqueValue){
        Boolean flag = redisTemplate.opsForValue()
                // 设置 10s 过期
                .setIfAbsent(REDIS_LOCK_KEY, uniqueValue, 100, TimeUnit.SECONDS);
        if (flag == null){
            return false;
        }
        return flag;
    }

    /**
     * 释放分布式锁
     * @param uniqueValue
     * @return
     */
    public void releaseLockV4(String uniqueValue){
        String value = (String) redisTemplate.opsForValue().get(REDIS_LOCK_KEY);
        if (!StringUtils.isEmpty(value) && Objects.equals(uniqueValue, value)){
            redisTemplate.delete(REDIS_LOCK_KEY);
        }
    }


    // ============= lua 使用 execute

    /**
     * 分布式锁使用lua 脚本
     * @param uniqueValue
     * @param expireTime 单位秒
     * @return
     */
    public Boolean getLockV5(String uniqueValue, Long expireTime){

        String script = "if redis.call('setnx',KEYS[1],ARGV[1])==1 then\n" +
                "    if redis.call('get',KEYS[1])==ARGV[1] then\n" +
                "        return redis.call('expire',KEYS[1],ARGV[2])\n" +
                "    else\n" +
                "        return 0\n" +
                "    end\n" +
                "else\n" +
                "    return 0\n" +
                "end";

        RedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        Long result = redisTemplate.execute(redisScript, Collections.singletonList(REDIS_LOCK_KEY),uniqueValue,expireTime);
        return Objects.equals(result, 1L);
    }

    /**
     * lua脚本释放分布式锁
     * @param uniqueValue
     * @return
     */
    public boolean releaseLockV5(String uniqueValue){
        String script = "if redis.call(\"get\",KEYS[1]) == ARGV[1] then\n" +
                "    return redis.call(\"del\",KEYS[1])\n" +
                "else\n" +
                "    return 0\n" +
                "end";
        RedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        Long result = redisTemplate.execute(redisScript, Collections.singletonList(REDIS_LOCK_KEY),uniqueValue);
        return Objects.equals(result, 1L);
    }



}
