package com.cybercloud.cameracapture;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

/**
 * lock test,fair/unfair
 */
public class ActivityLock extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    SyncLock syncLock = new SyncLock();

    Handler handler;
    Object lock = new Object();
    boolean notifyAll = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        HandlerThread handlerThread = new HandlerThread("lock");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

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
                    if(count==3){
                        try {
                            synchronized (lock){
                                Log.d(TAG,"lock a time: " + System.currentTimeMillis());
                                lock.wait();
                                Log.d(TAG,"lock a wait: ");
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
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
                    if(count==5){
                        try {
                            synchronized (lock){
                                Log.d(TAG,"lock b time: " + System.currentTimeMillis());
                                lock.wait();
                                Log.d(TAG,"lock b wait: ");
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }.start();



        if(notifyAll){
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        synchronized (lock){
                            lock.notifyAll();
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                        Log.d(TAG, "notifyAll Exception:" + e.getMessage());
                    }

                }
            },8000);
        }else {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        synchronized (lock){
                            lock.notify();
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                        Log.d(TAG, "notifyAll Exception:" + e.getMessage());
                    }

                }
            },8000);

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        synchronized (lock){
                            lock.notify();
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                        Log.d(TAG, "notifyAll Exception:" + e.getMessage());
                    }

                }
            },10000);
        }

    }
}