package com.cybercloud.cameracapture;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.cybercloud.cameracapture.media_component.ConditionSyncLock;

/**
 * lock test
 */
public class ActivityConditionLock extends AppCompatActivity {

    private static final String TAG = "MainActivityConditionLock";

    ConditionSyncLock lock = new ConditionSyncLock();

    Handler handler;
    boolean notifyAll = false;
    boolean condition = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        HandlerThread handlerThread = new HandlerThread("lock");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        if(condition){
            lockCondition();
        }else {
            lockDefault();
        }

    }

    private void lockCondition(){
        handler.post(new Runnable() {
            @Override
            public void run() {
                lock.lock();
            }
        });

        new Thread(){
            int count;
            @Override
            public void run() {
                while (true){
                    Log.d(TAG,"thread a");
                    try {
                        sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    count++;
                    if(count==4){
                        lock.lock("lockA");
                    }
                }
            }
        }.start();

        new Thread(){
            int count;
            @Override
            public void run() {
                while (true){
                    Log.d(TAG,"thread b");
                    try {
                        sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    count++;
                    if(count==3){
                        lock.lock("lockB");
                    }
                }
            }
        }.start();


        if(true){
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    lock.releaseLock();
                }
            }, 8000);
            return;
        }


        if(notifyAll){
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    lock.unlockAll();
                }
            },8000);
        }else {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    lock.unlock("lockB");
                }
            },8000);

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    lock.unlock("lockA");
                }
            },10000);
        }
    }

    private void lockDefault(){
        handler.post(new Runnable() {
            @Override
            public void run() {
                lock.lock();
            }
        });

        new Thread(){
            int count;
            @Override
            public void run() {
                while (true){
                    Log.d(TAG,"thread a");
                    try {
                        sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    count++;
                    if(count==4){
                        lock.lock();
                    }
                }
            }
        }.start();

        new Thread(){
            int count;
            @Override
            public void run() {
                while (true){
                    Log.d(TAG,"thread b");
                    try {
                        sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    count++;
                    if(count==3){
                        lock.lock();
                    }
                }
            }
        }.start();



        if(notifyAll){
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    lock.unlockAll();
                }
            },8000);
        }else {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    lock.unlock();
                }
            },8000);

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    lock.unlock();
                }
            },10000);
        }
    }
}