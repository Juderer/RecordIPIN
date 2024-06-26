/*
 * Copyright 2023 SHULI ZHU

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zhushuli.recordipin.activities.imu;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.alibaba.fastjson.JSON;
import com.zhushuli.recordipin.BaseActivity;
import com.zhushuli.recordipin.R;
import com.zhushuli.recordipin.models.imu.ImuInfo;
import com.zhushuli.recordipin.services.ImuService2;
import com.zhushuli.recordipin.views.ImuDynamicView;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author      : zhushuli
 * @createDate  : 2023/02/27 11:56
 * @description : 使用ImuService2更新折线图
 */
public class ImuDrawActivity2 extends BaseActivity {

    private final static String TAG = ImuDrawActivity2.class.getSimpleName();

    private ImuDynamicView imuView;

    private String mSensorType = null;

    private ImuService2 mImuService2;

    private final ServiceConnection mImuServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            ImuService2.ImuBinder mBinder = (ImuService2.ImuBinder) service;
            mImuService2 = mBinder.getImuService2();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
        }
    };

    private final BroadcastReceiver mImuReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive");
            String action = intent.getAction();
            if (action.equals(ImuService2.IMU_SENSOR_CHANGED_ACTION)) {
                Log.d(TAG, "onReceive:" + action);
                ImuInfo imuInfo = JSON.parseObject(intent.getStringExtra("IMU"), ImuInfo.class);
                Log.d(TAG, imuInfo.toString());
                if (mSensorType.equals("ACCEL") && (imuInfo.getType() == Sensor.TYPE_ACCELEROMETER ||
                        imuInfo.getType() == Sensor.TYPE_ACCELEROMETER_UNCALIBRATED)) {
                    imuView.addImuValue(imuInfo);
                }
                else if (mSensorType.equals("GYRO") && (imuInfo.getType() == Sensor.TYPE_GYROSCOPE ||
                        imuInfo.getType() == Sensor.TYPE_GYROSCOPE_UNCALIBRATED)) {
                    imuView.addImuValue(imuInfo);
                }
            }
        }
    };

    private final HandlerThread mReceiverThread = new HandlerThread("Receiver");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            String msg = getIntent().getStringExtra("Sensor");
            mSensorType = msg;
            getSupportActionBar().setTitle(mSensorType);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            Log.d(TAG, msg);
        } catch (NullPointerException e) {
            onDestroy();
        }

        setContentView(R.layout.activity_imu_draw);
        imuView = (ImuDynamicView) findViewById(R.id.imuDynamicView);

        Intent intent = new Intent(ImuDrawActivity2.this, ImuService2.class);
        bindService(intent, mImuServiceConn, BIND_AUTO_CREATE);

        mReceiverThread.start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        IntentFilter intent = new IntentFilter();
        intent.addAction(ImuService2.IMU_SENSOR_CHANGED_ACTION);
        registerReceiver(mImuReceiver, intent, null, new Handler(mReceiverThread.getLooper()));
        imuView.clearImuValue();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");

        unregisterReceiver(mImuReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        unbindService(mImuServiceConn);

        mReceiverThread.quitSafely();
    }
}