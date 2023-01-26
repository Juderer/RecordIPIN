package com.zhushuli.recordipin;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.zhushuli.recordipin.service.ImuService;
import com.zhushuli.recordipin.views.ImuDynamicView;

import java.util.concurrent.atomic.AtomicBoolean;

public class ImuDrawActivity extends AppCompatActivity {

    private final static String TAG = ImuDrawActivity.class.getSimpleName();

    private ImuDynamicView imuView;

    private String mSensorType = null;

    private ImuService mImuService;

    private final ServiceConnection mImuServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            ImuService.MyBinder mBinder = (ImuService.MyBinder) service;
            mImuService = mBinder.getImuService();

            mImuService.setCallback(new ImuService.Callback() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    Log.d(TAG, "onSensorChanged");
                    if (mSensorType.equals("ACCE") && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                        imuView.addImuValue(event);
                    }
                    else if (mSensorType.equals("GYRO") && event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                        imuView.addImuValue(event);
                    }
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
        }
    };

    private AtomicBoolean abConn = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            String msg = getIntent().getStringExtra("Sensor");
            mSensorType = msg;
            getSupportActionBar().setTitle(mSensorType);
            Log.d(TAG, msg);
        } catch (NullPointerException e) {
            onDestroy();
        }

        setContentView(R.layout.activity_imu_draw);
        imuView = (ImuDynamicView) findViewById(R.id.imuDynamicView);

        Intent intent = new Intent(ImuDrawActivity.this, ImuService.class);
        bindService(intent, mImuServiceConn, BIND_AUTO_CREATE);
        abConn.set(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        if (abConn.get()) {
            unbindService(mImuServiceConn);
        }
    }
}