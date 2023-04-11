package com.zhushuli.recordipin.services;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorEventListener2;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Queues;
import com.zhushuli.recordipin.models.imu.ImuInfo;
import com.zhushuli.recordipin.utils.FileUtils;
import com.zhushuli.recordipin.utils.ImuUtils;
import com.zhushuli.recordipin.utils.NotificationUtils;
import com.zhushuli.recordipin.utils.ThreadUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author : zhushuli
 * @createDate : 2023/04/07 21:08
 * @description : 重新修改IMU数据存储内容
 */
public class ImuService2 extends Service {

    private static final String TAG = ImuService2.class.getSimpleName();

    public static final String IMU_SENSOR_CHANGED_ACTION = "recordipin.broadcast.imu.sensorChanged";

    private SharedPreferences mSharedPreferences;

    private static final int ACCEL_RECORD_WRITE_CODE = 0x0100;

    private static final int ACCEL_RECORD_CLOSE_CODE = 0x0101;

    private static final int GYRO_RECORD_WRITE_CODE = 0x0200;

    private static final int GYRO_RECORD_CLOSE_CODE = 0x0201;

    private SensorManager mSensorManager;

    // 设置队列, 用于写入文件
    private BlockingQueue<String> mImuBlockingStrs = new ArrayBlockingQueue<String>(3000, true);
//    private BlockingQueue<SensorEvent> mEventQueue = new ArrayBlockingQueue<>(3000, true);
//    private Queue<String> mStringQueue = new LinkedList<>();

    private BlockingQueue<String> mAccelCsvs = new ArrayBlockingQueue<>(3000, true);

    private BlockingQueue<String> mGyroCsvs = new ArrayBlockingQueue<>(3000, true);

    private final SensorEventListener mAccelEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            Log.d(TAG, "onSensorChanged ACCEL");
            if (checkRecording()) {
                mAccelCsvs.offer(ImuUtils.genImuCsvV2(event));
            }
            sendBroadcast(new Intent(ImuService2.IMU_SENSOR_CHANGED_ACTION)
                    .putExtra("IMU", JSON.toJSONString(new ImuInfo(event))));
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.d(TAG, "onAccuracyChanged ACCEL");
        }
    };

    private final HandlerThread mAccelListenerThread = new HandlerThread("Acceleration Listener");

    private final SensorEventListener mGyroEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            Log.d(TAG, "onSensorChanged GYRO");
            if (checkRecording()) {
                mGyroCsvs.offer(ImuUtils.genImuCsvV2(event));
            }
            sendBroadcast(new Intent(ImuService2.IMU_SENSOR_CHANGED_ACTION)
                    .putExtra("IMU", JSON.toJSONString(new ImuInfo(event))));
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.d(TAG, "onAccuracyChanged GYRO");
        }
    };

    private final HandlerThread mGyroListenerThread = new HandlerThread("Gyroscope Listener");

    private final HandlerThread mListenThread = new HandlerThread("Listen");

    private final SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            Log.d(TAG, "onSensorChanged:" + ThreadUtils.threadID());
            if (checkRecording()) {
//                synchronized (new Object()) {
//                    // TODO::考虑使用两个子线程（配上两个SensorEventListener）分别监听加速度计与陀螺仪
//                }
                mImuBlockingStrs.offer(ImuUtils.genImuCsv(event));
//                mEventQueue.add(event);
//                mStringQueue.offer(ImuUtils.genImuCsv(event));
            }
            sendBroadcast(new Intent(IMU_SENSOR_CHANGED_ACTION).putExtra("IMU",
                    JSON.toJSONString(new ImuInfo(event))));
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
    private AccelRecordThread mAccelRecordThread;

    private GyroRecordThread mGyroRecordThread;

    // 判断写入子线程是否继续
    private AtomicBoolean recording = new AtomicBoolean(false);

    public boolean checkRecording() {
        return recording.get();
    }

    public void setRecording(boolean recording) {
        this.recording.set(recording);
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

    public ImuService2() {

    }

    public class ImuBinder extends Binder {
        public ImuService2 getImuService2() {
            return ImuService2.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return new ImuBinder();
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

        // 从参考项目（VideoIMUCapture：https://github.com/DavidGillsjo/VideoIMUCapture-Android）中了解的无校准类型
//        mAccelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED);
//        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        setRecording(mSharedPreferences.getBoolean("prefImuCollected", false));
        Log.d(TAG, String.valueOf(checkRecording()));

        registerResource();

        Notification notification = NotificationUtils.createNotification(this,
                "com.zhushuli.recordipin.imuservice", "IMU Service");
        startForeground(0x0001, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    private void registerResource() {
        mAccelListenerThread.start();
        mGyroListenerThread.start();
        mSensorManager.registerListener(
                mAccelEventListener,
                mAccelSensor,
                Integer.valueOf(mSharedPreferences.getString("prefImuFreq", "1")),
                new Handler(mAccelListenerThread.getLooper()));
        mSensorManager.registerListener(
                mGyroEventListener,
                mGyroSensor,
                Integer.valueOf(mSharedPreferences.getString("prefImuFreq", "1")),
                new Handler(mGyroListenerThread.getLooper()));

//        mSensorManager.registerListener(mSensorEventListener, mAccelSensor, SensorManager.SENSOR_DELAY_GAME, new Handler(mListenThread.getLooper()));
//        mSensorManager.registerListener(mSensorEventListener, mGyroSensor, SensorManager.SENSOR_DELAY_GAME, new Handler(mListenThread.getLooper()));

//        mSensorManager.registerListener(mSensorEventListener, mAccelSensor, SensorManager.SENSOR_DELAY_GAME);
//        mSensorManager.registerListener(mSensorEventListener, mGyroSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    private void unregisterResource() {
        mSensorManager.unregisterListener(mAccelEventListener, mAccelSensor);
        mSensorManager.unregisterListener(mGyroEventListener, mGyroSensor);
        mSensorManager.unregisterListener(mAccelEventListener);
        mSensorManager.unregisterListener(mGyroEventListener);
        ThreadUtils.interrupt(mAccelListenerThread);
        ThreadUtils.interrupt(mGyroListenerThread);
    }

    public synchronized void startImuRecord(String recordDir) {
        if (checkRecording() && mAccelRecordThread == null) {
            Log.d(TAG, "startImuRecord");
            mAccelRecordThread = new AccelRecordThread(recordDir);
            mGyroRecordThread = new GyroRecordThread(recordDir);
            new Thread(mAccelRecordThread).start();
            new Thread(mGyroRecordThread).start();
        } else {
            Log.d(TAG, "IMU record thread has been already RUNNING or IMU record is NOT allowed.");
        }
    }

    private class AccelRecordThread implements Runnable {

        private String mRecordDir;

        private BufferedWriter mBufferedWriter;

        private ArrayList<String> tmp = new ArrayList<>();

        public AccelRecordThread(String recordDir) {
            this.mRecordDir = recordDir;
        }

        private void initWriter() {
            mBufferedWriter = FileUtils.initWriter(mRecordDir + File.separator + "IMU", "ACCEL.csv");
            try {
                mBufferedWriter.write("sysClockTimeNanos,sysTimeMillis,eventTimestamp(Nanos),x,y,z,accuracy\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run() {
            initWriter();
            Log.d(TAG, "ACCEL Record Start");
            while (checkRecording() || mAccelCsvs.size() > 0) {
                try {
                    Queues.drain(mAccelCsvs, tmp, 500, 12, TimeUnit.SECONDS);
                    mBufferedWriter.write(String.join("", tmp));
                    mBufferedWriter.flush();
                    tmp.clear();
                    Log.d(TAG, "ACCEL Record Write");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            FileUtils.closeBufferedWriter(mBufferedWriter);
            Log.d(TAG, "ACCEL Record CLOSE");
        }
    }

    private class GyroRecordThread implements Runnable {

        private String mRecordDir;

        private BufferedWriter mBufferedWriter;

        private ArrayList<String> tmp = new ArrayList<>();

        public GyroRecordThread(String recordDir) {
            this.mRecordDir = recordDir;
        }

        private void initWriter() {
            mBufferedWriter = FileUtils.initWriter(mRecordDir + File.separator + "IMU", "GYRO.csv");
            try {
                mBufferedWriter.write("sysClockTime(Nanos),sysTime(Millis),eventTimestamp(Nanos),x,y,z,accuracy\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run() {
            initWriter();
            Log.d(TAG, "GYRO Record Start");
            while (checkRecording() || mGyroCsvs.size() > 0) {
                try {
                    Queues.drain(mGyroCsvs, tmp, 500, 12, TimeUnit.SECONDS);
                    mBufferedWriter.write(String.join("", tmp));
                    mBufferedWriter.flush();
                    tmp.clear();
                    Log.d(TAG, "GYRO Record Write");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            FileUtils.closeBufferedWriter(mBufferedWriter);
            Log.d(TAG, "GYRO Record CLOSE");
        }
    }

    @Deprecated
    private class RecordThread implements Runnable {
        private BufferedWriter mBufferedWriter;
        private String mRecordDir;
        private ArrayList<String> mStrings = new ArrayList<>();

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
            Log.d(TAG, "IMU Record Start");
            while (ImuService2.this.checkRecording() || mImuBlockingStrs.size() > 0) {
                try {
                    Queues.drain(mImuBlockingStrs, mStrings, 1500, 3000, TimeUnit.MILLISECONDS);
                    mBufferedWriter.write(String.join("", mStrings));
                    mBufferedWriter.flush();
                    Log.d(TAG, "IMU Record Write");
                    mStrings.clear();
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
//                            Log.d(TAG, "IMU Record write");
//                        }
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }

//                int writeCount = 0;
//                if (mEventQueue.size() > 0) {
//                    try {
//                        mBufferedWriter.write(ImuUtils.genImuCsv(mEventQueue.poll()));
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
            Log.d(TAG, "IMU Record End:" + Thread.currentThread().getId());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        unregisterResource();

        stopForeground(true);
        setRecording(false);
    }
}