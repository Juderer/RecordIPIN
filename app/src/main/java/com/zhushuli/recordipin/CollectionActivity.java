package com.zhushuli.recordipin;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.location.Location;
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
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.zhushuli.recordipin.models.imu.ImuInfo;
import com.zhushuli.recordipin.services.ImuService;
import com.zhushuli.recordipin.services.ImuService2;
import com.zhushuli.recordipin.services.LocationService;
import com.zhushuli.recordipin.services.LocationService2;
import com.zhushuli.recordipin.utils.DialogUtils;
import com.zhushuli.recordipin.utils.ThreadUtils;
import com.zhushuli.recordipin.utils.location.LocationUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

public class CollectionActivity extends AppCompatActivity {

    private static final String TAG = CollectionActivity.class.getSimpleName();

    // IMU相关控件
    private TextView tvAccelX;
    private TextView tvAccelY;
    private TextView tvAccelZ;

    private TextView tvGyroX;
    private TextView tvGyroY;
    private TextView tvGyroZ;

    // GNSS相关控件
    private TextView tvDate;
    private TextView tvCoordinate;
    private TextView tvSatellite;
    private TextView tvGnssTime;
    private TextView tvLocation;
    private TextView tvLocationAcc;
    private TextView tvSpeed;
    private TextView tvBearing;
    private TextView tvAltitude;

    private Button btnCollectData;

    // 定位服务相关类
    private LocationService2.LocationBinder mLocationBinder;

    private LocationService2 mLocationService2;

    private final ServiceConnection mLocationServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected Location");
            mLocationBinder = (LocationService2.LocationBinder) service;
            mLocationService2 = mLocationBinder.getLocationService2();

            mLocationService2.startLocationRecord(mRecordAbsDir);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected Location");
        }
    };

    // IMU服务相关类
    private ImuService2.ImuBinder mImuBinder;

    private ImuService2 mImuService2;

    private final ServiceConnection mImuServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected IMU");
            mImuBinder = (ImuService2.ImuBinder) service;
            mImuService2 = mImuBinder.getImuService2();

            mImuService2.startImuRecord(mRecordAbsDir);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected IMU");
        }
    };

    private final BroadcastReceiver mLocationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive Location:" + ThreadUtils.threadID());

            String action = intent.getAction();
            if (action.equals(LocationService2.GNSS_LOCATION_CHANGED_ACTION)) {
                Location location = intent.getParcelableExtra("Location");
                Message msg = Message.obtain();
                msg.what = LocationService2.GNSS_LOCATION_CHANGED_CODE;
                msg.obj = location;
                mMainHandler.sendMessage(msg);
            } else if (action.equals(LocationService2.GNSS_PROVIDER_DISABLED_ACTION)) {
                mMainHandler.sendEmptyMessage(LocationService2.GNSS_PROVIDER_DISABLED_CODE);
            }
        }
    };

    private final HandlerThread mLocationReceiveThread = new HandlerThread("Location Receiver");

    private final BroadcastReceiver mSatelliteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive Satellite:" + ThreadUtils.threadID());

            String action = intent.getAction();
            if (action.equals(LocationService2.GNSS_SATELLITE_STATUS_CHANGED_ACTION)) {
                Message msg = Message.obtain();
                msg.what = LocationService2.GNSS_SATELLITE_STATUS_CHANGED_CODE;
                msg.obj = intent.getStringExtra("Satellite");
                mMainHandler.sendMessage(msg);
            }
        }
    };

    private final HandlerThread mSatelliteReceiverThread = new HandlerThread("Satellite Receiver");

    private final BroadcastReceiver mImuReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive IMU:" + ThreadUtils.threadID());

            String action = intent.getAction();
            if (action.equals(ImuService2.IMU_SENSOR_CHANGED_ACTION)) {
                ImuInfo imuInfo = JSON.parseObject(intent.getStringExtra("IMU"), ImuInfo.class);
                Message msg = Message.obtain();
                msg.what = imuInfo.getType();
                msg.obj = imuInfo;
                mMainHandler.sendMessage(msg);
            }
        }
    };

    private final HandlerThread mImuReceiverThread = new HandlerThread("IMU Receiver");

    // 数据存储路径
    private final SimpleDateFormat storageFormatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

    private final SimpleDateFormat displayFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private String mRecordRootDir;

    private String mRecordAbsDir;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            SensorEvent event = null;
            switch (msg.what) {
                case LocationService2.GNSS_LOCATION_CHANGED_CODE:
                    tvCoordinate.setText("WGS84");
                    Location location = (Location) msg.obj;
                    tvDate.setText(displayFormatter.format(new Date(System.currentTimeMillis())));
                    tvGnssTime.setText(String.valueOf(location.getTime()));
                    tvLocation.setText(String.format("%.6f,%.6f", location.getLongitude(), location.getLatitude()));
                    tvLocationAcc.setText(String.format("%.2fm", location.getAccuracy()));
                    tvSpeed.setText(String.format("%.2fm/s,%.2fkm/h", location.getSpeed(), location.getSpeed() * 3.6));
                    tvBearing.setText(String.valueOf(location.getBearing()));
                    tvAltitude.setText(String.format("%.2fm", location.getAltitude()));
                    break;
                case LocationService2.GNSS_SATELLITE_STATUS_CHANGED_CODE:
                    tvSatellite.setText((String) msg.obj);
                    break;
                case LocationService2.GNSS_PROVIDER_DISABLED_CODE:
                    DialogUtils.showLocationSettingsAlert(CollectionActivity.this);
                    unbindServices();
                    setDefaultGnssInfo();
                    setDefaultImuInfo();
                    btnCollectData.setText("Collect");
                    break;
                case Sensor.TYPE_ACCELEROMETER:
                    ImuInfo accelInfo = (ImuInfo) msg.obj;
                    tvAccelX.setText(String.format("%.4f", accelInfo.values[0]));
                    tvAccelY.setText(String.format("%.4f", accelInfo.values[1]));
                    tvAccelZ.setText(String.format("%.4f", accelInfo.values[2]));
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    ImuInfo gyroInfo = (ImuInfo) msg.obj;
                    tvGyroX.setText(String.format("%.4f", gyroInfo.values[0]));
                    tvGyroY.setText(String.format("%.4f", gyroInfo.values[1]));
                    tvGyroZ.setText(String.format("%.4f", gyroInfo.values[2]));
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collection);
        Log.d(TAG, "onCreate");

        initImuView();
        initGnssView();
        btnCollectData = (Button) findViewById(R.id.btnCollectData);
        btnCollectData.setOnClickListener(this::onClick);

        mRecordRootDir = getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();

        mLocationReceiveThread.start();
        mSatelliteReceiverThread.start();
        mImuReceiverThread.start();
    }

    private void initImuView() {
        tvAccelX = (TextView) findViewById(R.id.tvAccelX);
        tvAccelY = (TextView) findViewById(R.id.tvAccelY);
        tvAccelZ = (TextView) findViewById(R.id.tvAccelZ);
        tvGyroX = (TextView) findViewById(R.id.tvGyroX);
        tvGyroY = (TextView) findViewById(R.id.tvGyroY);
        tvGyroZ = (TextView) findViewById(R.id.tvGyroZ);
    }

    private void initGnssView() {
        tvDate = (TextView) findViewById(R.id.tvDate);
        tvCoordinate = (TextView) findViewById(R.id.tvCoordinate);
        tvSatellite = (TextView) findViewById(R.id.tvSatellite);
        tvGnssTime = (TextView) findViewById(R.id.tvGnssTime);
        tvLocation = (TextView) findViewById(R.id.tvLocation);
        tvLocationAcc = (TextView) findViewById(R.id.tvLocationAcc);
        tvSpeed = (TextView) findViewById(R.id.tvSpeed);
        tvBearing = (TextView) findViewById(R.id.tvBearing);
        tvAltitude = (TextView) findViewById(R.id.tvAltitude);
    }

    private void bindServices() {
        // 绑定定位服务
        Intent locationIntent = new Intent(CollectionActivity.this, LocationService2.class);
        bindService(locationIntent, mLocationServiceConn, BIND_AUTO_CREATE);
        // 绑定IMU服务
        Intent imuIntent = new Intent(CollectionActivity.this, ImuService2.class);
        bindService(imuIntent, mImuServiceConn, BIND_AUTO_CREATE);

        btnCollectData.setText("Stop");
    }

    private void unbindServices() {
        // 服务解绑
        unbindService(mLocationServiceConn);
        unbindService(mImuServiceConn);

        btnCollectData.setText("Collect");
        setDefaultGnssInfo();
        setDefaultImuInfo();
    }

    private void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnCollectData:
                if (btnCollectData.getText().equals("Collect")) {
                    mRecordAbsDir = mRecordRootDir + File.separator + storageFormatter.format(new Date(System.currentTimeMillis()));
                    bindServices();
                } else {
                    unbindServices();
                }
                break;
            default:
                break;
        }
    }

    public Handler getHandler() {
        return mMainHandler;
    }

    private void setDefaultImuInfo() {
        tvAccelX.setText("--");
        tvAccelY.setText("--");
        tvAccelZ.setText("--");
        tvGyroX.setText("--");
        tvGyroY.setText("--");
        tvGyroZ.setText("--");
    }

    private void setDefaultGnssInfo() {
        tvDate.setText("--");
        tvCoordinate.setText("--");
        tvSatellite.setText("--");
        tvGnssTime.setText("--");
        tvLocation.setText("--");
        tvLocationAcc.setText("--");
        tvSpeed.setText("--");
        tvBearing.setText("--");
        tvAltitude.setText("--");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        IntentFilter locationIntent = new IntentFilter();
        locationIntent.addAction(LocationService2.GNSS_LOCATION_CHANGED_ACTION);
        locationIntent.addAction(LocationService2.GNSS_PROVIDER_DISABLED_ACTION);
        registerReceiver(mLocationReceiver, locationIntent, null, new Handler(mLocationReceiveThread.getLooper()));

        IntentFilter satelliteIntent = new IntentFilter();
        satelliteIntent.addAction(LocationService2.GNSS_SATELLITE_STATUS_CHANGED_ACTION);
        registerReceiver(mSatelliteReceiver, satelliteIntent, null, new Handler(mSatelliteReceiverThread.getLooper()));

        IntentFilter imuIntent = new IntentFilter();
        imuIntent.addAction(ImuService2.IMU_SENSOR_CHANGED_ACTION);
        registerReceiver(mImuReceiver, imuIntent, null, new Handler(mImuReceiverThread.getLooper()));
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");

        unregisterReceiver(mLocationReceiver);
        unregisterReceiver(mSatelliteReceiver);
        unregisterReceiver(mImuReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        if (btnCollectData.getText().equals("Stop")) {
            unbindServices();
        }

        ThreadUtils.interrupt(mLocationReceiveThread);
        ThreadUtils.interrupt(mSatelliteReceiverThread);
        ThreadUtils.interrupt(mImuReceiverThread);
    }
}