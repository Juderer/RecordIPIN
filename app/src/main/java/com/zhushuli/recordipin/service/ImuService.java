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

import java.util.concurrent.atomic.AtomicBoolean;

public class ImuService extends Service {

    private static final String TAG = "My" + ImuService.class.getSimpleName();

    private SensorManager mSensorManager;
    private SensorEventListener mSensorEventListener;
    private Sensor mAcceSensor;
    private Sensor mGyroSensor;
    private Sensor mMagSensor;

    private HandlerThread mSensorThread;
    private final AtomicBoolean abRegistered = new AtomicBoolean(false);

    private Callback callback = null;

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
                if (callback!=null) {
                    callback.onSensorChanged(event);
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
        if (!abRegistered.getAndSet(true)) {
            mSensorThread = new HandlerThread("Sensor Thread");
            mSensorThread.start();
            Handler mHandler = new Handler(mSensorThread.getLooper());

            mSensorManager.registerListener(mSensorEventListener, mAcceSensor, SensorManager.SENSOR_DELAY_GAME, mHandler);
            mSensorManager.registerListener(mSensorEventListener, mGyroSensor, SensorManager.SENSOR_DELAY_GAME, mHandler);
            mSensorManager.registerListener(mSensorEventListener, mMagSensor, SensorManager.SENSOR_DELAY_GAME, mHandler);
        }
    }

    private void unregisterResource() {
        if (abRegistered.getAndSet(false)) {
            mSensorManager.unregisterListener(mSensorEventListener, mAcceSensor);
            mSensorManager.unregisterListener(mSensorEventListener, mGyroSensor);
            mSensorManager.unregisterListener(mSensorEventListener, mMagSensor);
            mSensorManager.unregisterListener(mSensorEventListener);
            mSensorThread.quitSafely();
        }
    }

    public Callback getCallback() {
        return callback;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        unregisterResource();
    }
}