package com.zchzh.demo.spinlock;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author zengchzh
 * @date 2021/4/13
 */
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
