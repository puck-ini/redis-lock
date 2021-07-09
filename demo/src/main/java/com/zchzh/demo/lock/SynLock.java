package com.zchzh.demo.lock;

import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author zengchzh
 * @date 2021/4/13
 */
public class SynLock extends AbstractTestLock implements TestLock {

    @Override
    public void incr() {
        for (int i = 0; i< 10000; i++ ) {
            count++;
        }
    }

    @Override
    public synchronized void synIncr() {
        for (int i = 0; i< 10000; i++ ) {
            count++;
        }
    }
}
