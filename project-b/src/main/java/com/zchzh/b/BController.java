package com.zchzh.b;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zengchzh
 * @date 2021/1/21
 */

@RestController
@RequestMapping("/b")
public class BController {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisLockUtil redisLockUtil;

    private static final String UNIQUE_VALUE = "B";

    @RequestMapping("/dob")
    public void doSomething(){

    }
}
