package com.cybercloud.cameracapture;

/**
 * @author wonderful
 * @date 2021-4-28
 * @version v1.0
 * @descreption 同步锁
 */
public class SyncLock {

    private final Object lock = new Object();
    private volatile boolean isLock = false;
    private volatile int waitCount = 0;

    /**
     * 获取锁，如果别的线程拥有了锁则挂起当前线程
     */
    public void lock(){
        if(!isLock){
            isLock = true;
            return;
        }
        synchronized (lock){
            try {
                waitCount++;
                lock.wait();
                isLock = true;
            } catch (InterruptedException e) {
                waitCount--;
                e.printStackTrace();
            }
        }
    }

    /**
     * 释放锁，此方法为公平锁
     */
    public void unlock(){
        isLock = false;
        if(waitCount == 0)return;
        synchronized (lock){
            try {
                waitCount--;
                lock.notify();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    /**
     * 释放锁，此方法为非公平锁
     */
    public void unlockAll(){
        isLock = false;
        if(waitCount == 0)return;
        synchronized (lock){
            try {
                waitCount = 0;
                lock.notifyAll();
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
