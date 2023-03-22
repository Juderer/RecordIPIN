package com.zhushuli.recordipin.activities.imu;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.alibaba.fastjson2.JSON;
import com.zhushuli.recordipin.R;
import com.zhushuli.recordipin.models.imu.ImuInfo;
import com.zhushuli.recordipin.services.ImuService2;
import com.zhushuli.recordipin.utils.ThreadUtils;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author      : zhushuli
 * @createDate  : 2023/02/27 14:53
 * @description : 使用ImuService2在页面表格中展示IMU六轴数据
 */
public class ImuActivity2 extends AppCompatActivity {

    private static final String TAG = ImuActivity2.class.getSimpleName();

    private TextView tvAccelX;
    private TextView tvAccelY;
    private TextView tvAccelZ;

    private TextView tvGyroX;
    private TextView tvGyroY;
    private TextView tvGyroZ;

    private Button btnImuCollection;

    // 传感器数据显示精度
    private final DecimalFormat dfSensor = new DecimalFormat("#0.0000");

    // IMU服务相关类
    private ImuService2.ImuBinder mBinder = null;

    private ImuService2 mImuService2;

    private final ServiceConnection mImuServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            mBinder = (ImuService2.ImuBinder) service;
            mImuService2 = mBinder.getImuService2();

            mRecordAbsDir = mRecordRootDir + File.separator + formatter.format(new Date(System.currentTimeMillis()));
            mImuService2.startImuRecord(mRecordAbsDir);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
        }
    };

    // 时间戳转日期
    private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
    // 数据存储路径
    private String mRecordRootDir;
    // 数据存储路径
    private String mRecordAbsDir;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            ImuInfo imuInfo = (ImuInfo) msg.obj;
            switch (msg.what) {
                case Sensor.TYPE_ACCELEROMETER:
                case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
                    tvAccelX.setText(dfSensor.format(imuInfo.values[0]));
                    tvAccelY.setText(dfSensor.format(imuInfo.values[1]));
                    tvAccelZ.setText(dfSensor.format(imuInfo.values[2]));
                    break;
                case Sensor.TYPE_GYROSCOPE:
                case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                    tvGyroX.setText(String.format("%.4f", imuInfo.values[0]));
                    tvGyroY.setText(String.format("%.4f", imuInfo.values[1]));
                    tvGyroZ.setText(String.format("%.4f", imuInfo.values[2]));
                    break;
                default:
                    break;
            }
        }
    };

    public Handler getHandler() {
        return mMainHandler;
    }

    private final View.OnClickListener graphListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.tvAccelX:
                case R.id.tvAccelY:
                case R.id.tvAccelZ:
                    Intent accelGraphIntent = new Intent(ImuActivity2.this, ImuDrawActivity2.class);
                    accelGraphIntent.putExtra("Sensor", "ACCEL");
                    startActivity(accelGraphIntent);
                    break;
                case R.id.tvGyroX:
                case R.id.tvGyroY:
                case R.id.tvGyroZ:
                    Intent gyroGraphIntent = new Intent(ImuActivity2.this, ImuDrawActivity2.class);
                    gyroGraphIntent.putExtra("Sensor", "GYRO");
                    startActivity(gyroGraphIntent);
                    break;
                default:
                    break;
            }
        }
    };

    private final BroadcastReceiver mImuReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive");
            if (btnImuCollection.getText().equals("Start")) {
                return ;
            }

            String action = intent.getAction();
            Message msg = Message.obtain();
            if (action.equals(ImuService2.IMU_SENSOR_CHANGED_ACTION)) {
                ImuInfo imuInfo = JSON.parseObject(intent.getStringExtra("IMU"), ImuInfo.class);
                msg.what = imuInfo.getType();
                msg.obj = imuInfo;
                getHandler().sendMessage(msg);
            }
        }
    };

    private final HandlerThread mReceiverThread = new HandlerThread("Receiver");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_imu);
        Log.d(TAG, "onCreate:" + ThreadUtils.threadID());

        tvAccelX = (TextView) findViewById(R.id.tvAccelX);
        tvAccelY = (TextView) findViewById(R.id.tvAccelY);
        tvAccelZ = (TextView) findViewById(R.id.tvAccelZ);
        tvGyroX = (TextView) findViewById(R.id.tvGyroX);
        tvGyroY = (TextView) findViewById(R.id.tvGyroY);
        tvGyroZ = (TextView) findViewById(R.id.tvGyroZ);

        btnImuCollection = (Button) findViewById(R.id.btnImuStart);
        btnImuCollection.setOnClickListener(this::onClick);

        mRecordRootDir = getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();

        // TODO::满足在采集数据的同时可视化
        tvAccelX.setOnClickListener(graphListener);
        tvAccelY.setOnClickListener(graphListener);
        tvAccelZ.setOnClickListener(graphListener);
        tvGyroX.setOnClickListener(graphListener);
        tvGyroY.setOnClickListener(graphListener);
        tvGyroZ.setOnClickListener(graphListener);

        mReceiverThread.start();
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnImuStart:
                if (btnImuCollection.getText().equals("Start")) {
                    Intent intent = new Intent(ImuActivity2.this, ImuService2.class);
                    bindService(intent, mImuServiceConnection, BIND_AUTO_CREATE);

                    btnImuCollection.setText("Stop");
                } else {
                    unbindService(mImuServiceConnection);
                    btnImuCollection.setText("Start");
                }
                break;
            default:
                break;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        IntentFilter intent = new IntentFilter();
        intent.addAction(ImuService2.IMU_SENSOR_CHANGED_ACTION);
        registerReceiver(mImuReceiver, intent, null, new Handler(mReceiverThread.getLooper()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        unregisterReceiver(mImuReceiver);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        if (btnImuCollection.getText().equals("Stop")) {
            unbindService(mImuServiceConnection);
        }
        mReceiverThread.quitSafely();
    }
}