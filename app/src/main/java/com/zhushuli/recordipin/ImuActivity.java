package com.zhushuli.recordipin;

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

import com.zhushuli.recordipin.service.ImuService;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ImuActivity extends AppCompatActivity {

    private static final String TAG = "My" + ImuActivity.class.getSimpleName();

    private TextView tvAcceX;
    private TextView tvAcceY;
    private TextView tvAcceZ;
    private TextView tvGyroX;
    private TextView tvGyroY;
    private TextView tvGyroZ;

    private CheckBox cbRecord;
    private Button btnImuCollection;

    private boolean bRecording = true;

    private DecimalFormat dfSensor = new DecimalFormat("#0.0000");  // 传感器数据显示精度

    // IMU服务相关类
    private ImuService.MyBinder mBinder = null;
    private ImuService mImuService;
    private ServiceConnection mImuServiceConnection;

    // 数据存储路径
    private SimpleDateFormat formatter;
    private String mRecordingDir;

    private Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            SensorEvent event = (SensorEvent) msg.obj;
            switch (msg.what) {
                case Sensor.TYPE_ACCELEROMETER:
                    tvAcceX.setText(dfSensor.format(event.values[0]));
                    tvAcceY.setText(dfSensor.format(event.values[1]));
                    tvAcceZ.setText(dfSensor.format(event.values[2]));
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    tvGyroX.setText(String.format("%.4f", event.values[0]));
                    tvGyroY.setText(String.format("%.4f", event.values[1]));
                    tvGyroZ.setText(String.format("%.4f", event.values[2]));
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_imu);
        Log.d(TAG, "onCreate");

        tvAcceX = (TextView) findViewById(R.id.tvAcceX);
        tvAcceY = (TextView) findViewById(R.id.tvAcceY);
        tvAcceZ = (TextView) findViewById(R.id.tvAcceZ);
        tvGyroX = (TextView) findViewById(R.id.tvGyroX);
        tvGyroY = (TextView) findViewById(R.id.tvGyroY);
        tvGyroZ = (TextView) findViewById(R.id.tvGyroZ);

        cbRecord = (CheckBox) findViewById(R.id.cbRecord);
        cbRecord.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    bRecording = true;
                } else {
                    bRecording = false;
                }
                Log.d(TAG, "onCheckedChanged");
            }
        });

        btnImuCollection = (Button) findViewById(R.id.btnImuStart);
        btnImuCollection.setOnClickListener(this::onClick);

        initImuServiceConnection();

        formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        mRecordingDir = getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();
    }

    private void initImuServiceConnection() {
        mImuServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "onServiceConnected");
                mBinder = (ImuService.MyBinder) service;
                mImuService = mBinder.getImuService();

                // 数据存储路径
                if (bRecording) {
                    String recordingDir = mRecordingDir + File.separator + formatter.format(new Date(System.currentTimeMillis()));
                    mImuService.startImuRecording(recordingDir);
                }

                mImuService.setCallback(new ImuService.Callback() {
                    @Override
                    public void onSensorChanged(SensorEvent event) {
                        Message msg = Message.obtain();
                        switch (event.sensor.getType()) {
                            case Sensor.TYPE_ACCELEROMETER:
                                msg.what = Sensor.TYPE_ACCELEROMETER;
                                break;
                            case Sensor.TYPE_GYROSCOPE:
                                msg.what = Sensor.TYPE_GYROSCOPE;
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

            }
        };
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
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        if (btnImuCollection.getText().equals("Stop")) {
            unbindService(mImuServiceConnection);
        }
    }
}