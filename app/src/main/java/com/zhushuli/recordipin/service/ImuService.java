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
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.collect.Queues;
import com.zhushuli.recordipin.utils.FileUtils;
import com.zhushuli.recordipin.utils.ImuUtils;

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
    private SensorEventListener mSensorEventListener;
    private Sensor mAccelSensor;
    private Sensor mGyroSensor;
    private Sensor mMagSensor;

    private Callback callback = null;

    // SensorEvent数据预处理
    private HandlerThread mSensorThread;
    private Handler mHandler;

    // 设置队列, 用于写入文件
    private Queue<String> mStrQueue = new LinkedList<>();
    private BlockingQueue<String> mBlockingQueue = new ArrayBlockingQueue<String>(3000, true);
    // 传感器数据写入文件子线程
    private ImuRecordThread mImuRecordThread;
    private AtomicBoolean abRunning = new AtomicBoolean(false);

    public ImuService() {

    }

    public class MyBinder extends Binder {
        public ImuService getImuService() {
            return ImuService.this;
        }
    }

    public interface Callback {
        void onSensorChanged(SensorEvent event);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return new MyBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        mSensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        mAccelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mMagSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        mSensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                Log.d(TAG, "onSensorChanged");
                if (callback != null) {
                    callback.onSensorChanged(event);

//                    Message msg = Message.obtain();
//                    msg.obj = event;
//                    mHandler.sendMessage(msg);

                    mBlockingQueue.offer(ImuUtils.sensorEvent2Str(event));
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                Log.d(TAG, "onAccuracyChanged");
            }
        };

        registerResource();
    }

    private void registerResource() {
        mSensorThread = new HandlerThread("Sensor Thread", Thread.MAX_PRIORITY);
        mSensorThread.start();
        mHandler = new Handler(mSensorThread.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                // 使用该Handler处理会造成时间戳计算出现问题
                // 且放在主线程中处理不会造成太大时间消耗
                SensorEvent event = (SensorEvent) msg.obj;
                mBlockingQueue.offer(ImuUtils.sensorEvent2Str(event));
                Log.d(TAG, "event handler");
            }
        };

        mSensorManager.registerListener(mSensorEventListener, mAccelSensor, SensorManager.SENSOR_DELAY_GAME, mHandler);
        mSensorManager.registerListener(mSensorEventListener, mGyroSensor, SensorManager.SENSOR_DELAY_GAME, mHandler);
        mSensorManager.registerListener(mSensorEventListener, mMagSensor, SensorManager.SENSOR_DELAY_GAME, mHandler);
    }

    private void unregisterResource() {
        mSensorManager.unregisterListener(mSensorEventListener, mAccelSensor);
        mSensorManager.unregisterListener(mSensorEventListener, mGyroSensor);
        mSensorManager.unregisterListener(mSensorEventListener, mMagSensor);
        mSensorManager.unregisterListener(mSensorEventListener);
        mSensorThread.quitSafely();
    }

    public void startImuRecording(String mRecordingDir) {
        abRunning.set(true);
        mImuRecordThread = new ImuRecordThread(mRecordingDir);
        new Thread(mImuRecordThread).start();
    }

    private class ImuRecordThread implements Runnable {
        private BufferedWriter mBufferedWriter;
        private String mRecordingDir;
        private ArrayList<String> mStringList = new ArrayList<>();

        public ImuRecordThread(String recordingDir) {
            mRecordingDir = recordingDir;
        }

        private void initWriter() {
            mBufferedWriter = FileUtils.initWriter(mRecordingDir, "IMU.csv");
        }

        @Override
        public void run() {
            initWriter();
            Log.d(TAG, "IMU recording start");
            while (ImuService.this.getAbRunning()) {
                try {
                    Queues.drain(mBlockingQueue, mStringList, 1500, 3000, TimeUnit.MILLISECONDS);
                    mBufferedWriter.write(String.join("", mStringList));
                    mBufferedWriter.flush();
                    Log.d(TAG, "IMU recording write");
                    mStringList.clear();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
            FileUtils.closeBufferedWriter(mBufferedWriter);
            Log.d(TAG, "IMU recording end");
        }
    }

    public Callback getCallback() {
        return callback;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public boolean getAbRunning() {
        return abRunning.get();
    }

    public void setAbRunning(boolean bRunning) {
        abRunning.set(bRunning);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        unregisterResource();

        abRunning.set(false);
    }
}