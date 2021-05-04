package com.cybercloud.cameracapture.media_component;

import android.util.Log;
import java.util.HashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author wonderful
 * @date 2021-4-28
 * @version v1.0
 * @descreption 同步锁
 */
public class ConditionSyncLock {

    private static final String TAG = "ConditionSyncLock";

    private final ReentrantLock lock;
    private final HashMap<String, Condition> conditionMap;
    private volatile boolean isLock;

    public ConditionSyncLock(){
        lock = new ReentrantLock();
        conditionMap = new HashMap<>();
        conditionMap.put(getClass().getName(),lock.newCondition());
        isLock = false;
    }

    /**
     * 获取默认condition，下面不带参数的锁操作都使用默认condition
     * @return
     */
    private Condition defaultCondition(){
        return conditionMap.get(getClass().getName());
    }

    /**
     * 获取锁，如果别的线程拥有了锁则挂起当前线程
     * 必须与 unlock 或 unlockAll成对出现
     */
    public void lock(){
        if(!isLock){
            isLock = true;
            return;
        }

        try {
            lock.lock();
            defaultCondition().await();
            isLock = true;
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            lock.unlock();
        }
    }

    /**
     * 释放锁，此方法为公平锁
     */
    public void unlock(){
        isLock = false;

        try {
            lock.lock();
            defaultCondition().signal();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            lock.unlock();
        }
    }

    /**
     * 释放所有默认condition条件的锁，todo 此方法为公平锁  ???
     */
    public void unlockAll(){
        isLock = false;

        try {
            lock.lock();
            defaultCondition().signalAll();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            lock.unlock();
        }
    }


    /**
     * 获取锁，如果别的线程拥有了锁则挂起当前线程
     * 必须与unlock(String conditionKey)或unlockAll(String conditionKey)成对出现
     * @param conditionKey 条件key
     */
    public void lock(String conditionKey){
        if(!isLock){
            isLock = true;
            return;
        }

        Condition condition = conditionMap.get(conditionKey);
        if(condition == null){
            condition = lock.newCondition();
            conditionMap.put(conditionKey,condition);
        }

        try {
            lock.lock();
            condition.await();
            isLock = true;
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            lock.unlock();
        }
    }


    /**
     * 释放condition条件的锁，此方法为公平锁
     * @param conditionKey 条件key
     */
    public void unlock(String conditionKey){
        isLock = false;

        Condition condition = conditionMap.get(conditionKey);
        if(condition == null){
            Log.e(TAG, "unlock with invalid key: " + conditionKey);
            return;
        }

        try {
            lock.lock();
            condition.signal();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            lock.unlock();
        }
    }

    /**
     * 释放所有condition条件的锁，todo 此方法为公平锁 ???
     * @param conditionKey 条件key
     */
    public void unlockAll(String conditionKey){
        isLock = false;

        Condition condition = conditionMap.get(conditionKey);
        if(condition == null){
            Log.e(TAG, "unlock with invalid key: " + conditionKey);
            return;
        }

        try {
            lock.lock();
            condition.signalAll();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            lock.unlock();
        }
    }

    /**
     * 非阻塞锁，必须与nonBlockingUnlock成对出现
     * @return true:获取到锁 false:锁被其他调用者持有
     */
    public synchronized boolean nonBlockingLock(){
        if(isLock)return false;
        isLock = true;
        return true;
    }

    /**
     * 释放锁
     */
    public synchronized void nonBlockingUnlock(){
        isLock = false;
    }

    /**
     * 获取锁的状态
     * @return
     */
    public synchronized boolean isLock(){
        return isLock;
    }

    /**
     * 释放所有锁
     */
    public void releaseLock(){
        for(String key:conditionMap.keySet()){
            unlockAll(key);
        }
    }
}
