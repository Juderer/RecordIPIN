package com.zhushuli.recordipin.activities.imu;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.zhushuli.recordipin.R;
import com.zhushuli.recordipin.services.ImuService;
import com.zhushuli.recordipin.utils.ThreadUtils;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

@Deprecated
public class ImuActivity extends AppCompatActivity {

    private static final String TAG = ImuActivity.class.getSimpleName();

    private TextView tvAccelX;
    private TextView tvAccelY;
    private TextView tvAccelZ;

    private TextView tvGyroX;
    private TextView tvGyroY;
    private TextView tvGyroZ;

    private CheckBox cbRecord;
    private Button btnImuCollection;

    // 传感器数据显示精度
    private final DecimalFormat dfSensor = new DecimalFormat("#0.0000");

    // IMU服务相关类
    private ImuService.MyBinder mBinder = null;

    private ImuService mImuService;

    private final ServiceConnection mImuServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            mBinder = (ImuService.MyBinder) service;
            mImuService = mBinder.getImuService();

            // 数据存储路径
            if (recording.get()) {
                mRecordAbsDir = mRecordRootDir + File.separator + formatter.format(new Date(System.currentTimeMillis()));
                mImuService.startImuRecord(mRecordAbsDir);
            }

            mImuService.setCallback(new ImuService.Callback() {
                @Override
                public void onSensorChanged(SensorEvent event) {
                    Log.d(TAG, "onSensorChanged:" + ThreadUtils.threadID());
                    Message msg = Message.obtain();
                    switch (event.sensor.getType()) {
                        case Sensor.TYPE_ACCELEROMETER:
                            msg.what = Sensor.TYPE_ACCELEROMETER;
                            break;
                        case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
                            msg.what = Sensor.TYPE_ACCELEROMETER_UNCALIBRATED;
                            break;
                        case Sensor.TYPE_GYROSCOPE:
                            msg.what = Sensor.TYPE_GYROSCOPE;
                            break;
                        case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                            msg.what = Sensor.TYPE_GYROSCOPE_UNCALIBRATED;
                            break;
                        case Sensor.TYPE_MAGNETIC_FIELD:
                            msg.what = Sensor.TYPE_MAGNETIC_FIELD;
                            break;
                        default:
                            break;
                    }
                    msg.obj = event;
                    ImuActivity.this.getHandler().sendMessage(msg);
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
        }
    };

    // 是否存储IMU数据
    private AtomicBoolean recording = new AtomicBoolean(false);
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
            SensorEvent event = (SensorEvent) msg.obj;
            switch (msg.what) {
                case Sensor.TYPE_ACCELEROMETER:
                case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
                    tvAccelX.setText(dfSensor.format(event.values[0]));
                    tvAccelY.setText(dfSensor.format(event.values[1]));
                    tvAccelZ.setText(dfSensor.format(event.values[2]));
                    break;
                case Sensor.TYPE_GYROSCOPE:
                case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                    tvGyroX.setText(String.format("%.4f", event.values[0]));
                    tvGyroY.setText(String.format("%.4f", event.values[1]));
                    tvGyroZ.setText(String.format("%.4f", event.values[2]));
                    break;
                default:
                    break;
            }
        }
    };

    private final View.OnClickListener graphListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.tvAccelX:
                case R.id.tvAccelY:
                case R.id.tvAccelZ:
                    Intent accelGraphIntent = new Intent(ImuActivity.this, ImuDrawActivity.class);
                    accelGraphIntent.putExtra("Sensor", "ACCEL");
                    startActivity(accelGraphIntent);
                    break;
                case R.id.tvGyroX:
                case R.id.tvGyroY:
                case R.id.tvGyroZ:
                    Intent gyroGraphIntent = new Intent(ImuActivity.this, ImuDrawActivity.class);
                    gyroGraphIntent.putExtra("Sensor", "GYRO");
                    startActivity(gyroGraphIntent);
                    break;
                default:
                    break;
            }
            if (btnImuCollection.getText().equals("Stop")) {
                unbindService(mImuServiceConnection);
                btnImuCollection.setText("Start");
                cbRecord.setEnabled(true);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_imu);
        Log.d(TAG, "onCreate");

        tvAccelX = (TextView) findViewById(R.id.tvAccelX);
        tvAccelY = (TextView) findViewById(R.id.tvAccelY);
        tvAccelZ = (TextView) findViewById(R.id.tvAccelZ);
        tvGyroX = (TextView) findViewById(R.id.tvGyroX);
        tvGyroY = (TextView) findViewById(R.id.tvGyroY);
        tvGyroZ = (TextView) findViewById(R.id.tvGyroZ);

        cbRecord = (CheckBox) findViewById(R.id.cbRecord);
        cbRecord.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    recording.set(true);
                }
                else {
                    recording.set(false);
                }
                Log.d(TAG, "onCheckedChanged");
            }
        });

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
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnImuStart:
                if (btnImuCollection.getText().equals("Start")) {
                    Intent intent = new Intent(ImuActivity.this, ImuService.class);
                    bindService(intent, mImuServiceConnection, BIND_AUTO_CREATE);

                    btnImuCollection.setText("Stop");
                    cbRecord.setEnabled(false);
                } else {
                    unbindService(mImuServiceConnection);
                    btnImuCollection.setText("Start");
                    cbRecord.setEnabled(true);
                }
                break;
            default:
                break;
        }
    }

    public Handler getHandler() {
        return mMainHandler;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
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
    }
}