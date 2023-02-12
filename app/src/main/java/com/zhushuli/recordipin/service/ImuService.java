package com.zhushuli.recordipin.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.collect.Queues;
import com.zhushuli.recordipin.utils.FileUtils;
import com.zhushuli.recordipin.utils.ImuUtils;
import com.zhushuli.recordipin.utils.ThreadUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ImuService extends Service {

    private static final String TAG = "My" + ImuService.class.getSimpleName();

    private SensorManager mSensorManager;

    // 设置队列, 用于写入文件
    private BlockingQueue<String> mBlockingQueue = new ArrayBlockingQueue<String>(3000, true);
//    private BlockingQueue<SensorEvent> mEventQueue = new ArrayBlockingQueue<>(3000, true);
//    private Queue<String> mStringQueue = new LinkedList<>();

    // SensorEvent数据预处理
    private final HandlerThread mListenThread = new HandlerThread("Listen");

    private final SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
//            Log.d(TAG, "onSensorChanged:" + ThreadUtils.threadID());
            if (callback != null) {
                callback.onSensorChanged(event);
                if (checkRecord()) {
                    mBlockingQueue.offer(ImuUtils.sensorEvent2Str(event));
//                    mEventQueue.add(event);
//                    mStringQueue.offer(ImuUtils.sensorEvent2Str(event));
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.d(TAG, "onAccuracyChanged");
        }
    };

    // 加速度计
    private Sensor mAccelSensor;
    // 陀螺仪
    private Sensor mGyroSensor;

    // 传感器数据写入文件子线程
    private RecordThread mRecordThread;
    // 判断写入子线程是否继续
    private AtomicBoolean abRecord = new AtomicBoolean(false);

    public boolean checkRecord() {
        return abRecord.get();
    }

    public void setRecord(boolean record) {
        abRecord.set(record);
    }

    // 回调接口
    public interface Callback {
        void onSensorChanged(SensorEvent event);
    }

    private Callback callback = null;

    public Callback getCallback() {
        return callback;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public ImuService() {

    }

    public class MyBinder extends Binder {
        public ImuService getImuService() {
            return ImuService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return new MyBinder();
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        Log.d(TAG, "onRebind");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate" + Thread.currentThread().getId());

        mSensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        mAccelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        registerResource();
    }

    private void registerResource() {
        mListenThread.start();
        mSensorManager.registerListener(mSensorEventListener, mAccelSensor, SensorManager.SENSOR_DELAY_GAME, new Handler(mListenThread.getLooper()));
        mSensorManager.registerListener(mSensorEventListener, mGyroSensor, SensorManager.SENSOR_DELAY_GAME, new Handler(mListenThread.getLooper()));

//        mSensorManager.registerListener(mSensorEventListener, mAccelSensor, SensorManager.SENSOR_DELAY_GAME);
//        mSensorManager.registerListener(mSensorEventListener, mGyroSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    private void unregisterResource() {
        mSensorManager.unregisterListener(mSensorEventListener, mAccelSensor);
        mSensorManager.unregisterListener(mSensorEventListener, mGyroSensor);
        mSensorManager.unregisterListener(mSensorEventListener);
        mListenThread.quitSafely();
    }

    public void startImuRecording(String mRecordingDir) {
        abRecord.set(true);
        mRecordThread = new RecordThread(mRecordingDir);
        new Thread(mRecordThread).start();
    }

    private class RecordThread implements Runnable {
        private BufferedWriter mBufferedWriter;
        private String mRecordDir;
        private ArrayList<String> mStringList = new ArrayList<>();

        public RecordThread(String recordDir) {
            mRecordDir = recordDir;
        }

        private void initWriter() {
            mBufferedWriter = FileUtils.initWriter(mRecordDir, "IMU.csv");
            try {
                mBufferedWriter.write("sensor,sysTime,elapsedTime,x,y,z,accuracy\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            initWriter();
            Log.d(TAG, "Record Thread Start");
            while (ImuService.this.checkRecord() || mBlockingQueue.size() > 0) {
                try {
                    Queues.drain(mBlockingQueue, mStringList, 1500, 3000, TimeUnit.MILLISECONDS);
                    mBufferedWriter.write(String.join("", mStringList));
                    mBufferedWriter.flush();
                    Log.d(TAG, "Record Thread Write");
                    mStringList.clear();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }

//                int writeCount = 0;
//                if (mStringQueue.size() > 0) {
//                    try {
//                        mBufferedWriter.write(mStringQueue.poll());
//                        writeCount ++;
//                        if (writeCount > 1500) {
//                            mBufferedWriter.flush();
//                            Log.d(TAG, "IMU recording write");
//                        }
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }

//                int writeCount = 0;
//                if (mEventQueue.size() > 0) {
//                    try {
//                        mBufferedWriter.write(ImuUtils.sensorEvent2Str(mEventQueue.poll()));
//                        writeCount ++;
//                        if (writeCount > 1500) {
//                            mBufferedWriter.flush();
//                            Log.d(TAG, "IMU Record Write");
//                        }
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
            }
            FileUtils.closeBufferedWriter(mBufferedWriter);
            Log.d(TAG, "Record Thread End:" + Thread.currentThread().getId());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        unregisterResource();

        setRecord(false);
    }
}