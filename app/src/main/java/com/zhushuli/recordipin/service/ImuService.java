package com.zhushuli.recordipin.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.collect.Queues;
import com.zhushuli.recordipin.utils.FileUtils;
import com.zhushuli.recordipin.utils.ImuStrUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
    private Sensor mAcceSensor;
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

    // 解决SensorEvent时间戳问题
    private long sensorTimeReference = 0L;
    private long myTimeReference = 0L;

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
        mAcceSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mMagSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        mSensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
//                Log.d(TAG, "onSensorChanged");
                if (callback != null) {
                    callback.onSensorChanged(event);
                    Message msg = Message.obtain();
                    msg.obj = event;
                    mHandler.sendMessage(msg);
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
                SensorEvent event = (SensorEvent) msg.obj;
                // set reference time
                if (sensorTimeReference == 0L && myTimeReference == 0L) {
                    sensorTimeReference = event.timestamp;
                    myTimeReference = System.currentTimeMillis();
                }
                // set event timestamp to current time in milliseconds
                event.timestamp = myTimeReference +
                        Math.round((event.timestamp - sensorTimeReference) / 1000000L);
                mBlockingQueue.offer(ImuStrUtils.sensorEvent2Str(event));
//                Log.d(TAG, "event handler");
            }
        };

        mSensorManager.registerListener(mSensorEventListener, mAcceSensor, SensorManager.SENSOR_DELAY_GAME, mHandler);
        mSensorManager.registerListener(mSensorEventListener, mGyroSensor, SensorManager.SENSOR_DELAY_GAME, mHandler);
        mSensorManager.registerListener(mSensorEventListener, mMagSensor, SensorManager.SENSOR_DELAY_GAME, mHandler);
    }

    private void unregisterResource() {
        mSensorManager.unregisterListener(mSensorEventListener, mAcceSensor);
        mSensorManager.unregisterListener(mSensorEventListener, mGyroSensor);
        mSensorManager.unregisterListener(mSensorEventListener, mMagSensor);
        mSensorManager.unregisterListener(mSensorEventListener);
        mSensorThread.quitSafely();
    }

    public void startImuRecording() {
        abRunning.set(true);
        // 存储文件路径
        String mRecordingDir = getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();
        mImuRecordThread = new ImuRecordThread(mRecordingDir);
        new Thread(mImuRecordThread).start();
    }

    private class ImuRecordThread implements Runnable {
        private BufferedWriter mBufferedWriter;
        private String mDirRootPath;
        private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

        public ImuRecordThread(String dirRootPath) {
            mDirRootPath = dirRootPath;
        }

        private void initWriter() {
            String dirPath = mDirRootPath + File.separator + formatter.format(new Date(System.currentTimeMillis()));
            mBufferedWriter = FileUtils.initWriter(dirPath, "IMU.csv");
        }

        @Override
        public void run() {
            initWriter();
            Log.d(TAG, "IMU recording start");
            while (ImuService.this.getAbRunning()) {
                try {
                    ArrayList<String> list = new ArrayList<>();
                    Queues.drain(mBlockingQueue, list, 750, 5, TimeUnit.SECONDS);
                    mBufferedWriter.write(String.join("", list));
                    mBufferedWriter.flush();
                    Log.d(TAG, "IMU recording write");
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
            try {
                mBufferedWriter.close();
                Log.d(TAG, "IMU recording end");
            } catch (IOException e) {
                e.printStackTrace();
            }
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