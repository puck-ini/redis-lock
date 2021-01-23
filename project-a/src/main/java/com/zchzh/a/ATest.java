package com.zchzh.a;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;


/**
 * @author zengchzh
 * @date 2021/1/21
 */


@SpringBootTest
public class ATest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisLockUtil redisLockUtil;

    private static final String UNIQUE_VALUE = "A";

    private static final String SHARE_KEY = "share_key";



    @Test
    public void doSomething(){
        try {
            if (redisLockUtil.getLockV1()){
                // 返回旧值 设置新值
                redisTemplate.opsForValue().getAndSet(SHARE_KEY, "test");
            }
        } finally {
            redisLockUtil.releaseLockV1();
        }

    }

    @Test
    public void getLock(){
        redisLockUtil.getLockV3();
    }

    @Test
    public void releaseLock(){
        redisLockUtil.releaseLockV3();
    }

    @Test
    public void getLockUnique() {
        Assert.assertTrue(redisLockUtil.getLockV4(UNIQUE_VALUE));
    }

    @Test
    public void releaseLockUnique() {
        redisLockUtil.releaseLockV4(UNIQUE_VALUE);
    }

    @Test
    public void getLockLua() {
        Assert.assertTrue(redisLockUtil.getLockV5(UNIQUE_VALUE, 100L));
    }

    @Test
    public void releaseLockLua() {
        Assert.assertTrue(redisLockUtil.releaseLockV5(UNIQUE_VALUE));
    }
}
