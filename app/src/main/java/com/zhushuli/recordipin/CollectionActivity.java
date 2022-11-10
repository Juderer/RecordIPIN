package com.zhushuli.recordipin;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.zhushuli.recordipin.service.ImuService;
import com.zhushuli.recordipin.service.LocationService;
import com.zhushuli.recordipin.utils.DialogUtils;
import com.zhushuli.recordipin.utils.LocationUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

public class CollectionActivity extends AppCompatActivity {

    private static final String TAG = "My" + CollectionActivity.class.getSimpleName();

    private static final int GNSS_LOCATION_UPDATE_CODE = 8402;
    private static final int GNSS_SEARCHING_CODE = 502;
    private static final int GNSS_PROVIDER_DISABLED_CODE = 404;

    // IMU相关控件
    private TextView tvAccelX;
    private TextView tvAccelY;
    private TextView tvAccelZ;
    private TextView tvGyroX;
    private TextView tvGyroY;
    private TextView tvGyroZ;
    private TextView tvMagX;
    private TextView tvMagY;
    private TextView tvMagZ;
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
    private LocationService.MyBinder mLocationBinder;
    private LocationService mLocationService;
    private ServiceConnection mLocationServiceConnection;

    // IMU服务相关类
    private ImuService.MyBinder mImuBinder;
    private ImuService mImuService;
    private ServiceConnection mImuServiceConnection;

    // 数据存储路径
    private SimpleDateFormat formatter;
    private String mRecordingDir;

    private Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            SensorEvent event = null;
            switch (msg.what) {
                case GNSS_LOCATION_UPDATE_CODE:
                    tvSatellite.setText("--");

                    tvCoordinate.setText("WGS84");
                    HashMap<String, String> map = (HashMap<String, String>) msg.obj;
                    tvDate.setText(map.get("date"));
                    tvGnssTime.setText(map.get("time"));
                    tvLocation.setText(map.get("location"));
                    tvLocationAcc.setText(map.get("accuracy"));
                    tvSpeed.setText(map.get("speed"));
                    tvBearing.setText(map.get("bearing"));
                    tvAltitude.setText(map.get("altitude"));
                    break;
                case GNSS_SEARCHING_CODE:
                    setDefaultGnssInfo();
                    tvSatellite.setText((String) msg.obj);
                    break;
                case GNSS_PROVIDER_DISABLED_CODE:
                    DialogUtils.showLocationSettingsAlert(CollectionActivity.this);
                    break;
                case Sensor.TYPE_ACCELEROMETER:
                    event = (SensorEvent) msg.obj;
                    tvAccelX.setText(String.format("%.4f", event.values[0]));
                    tvAccelY.setText(String.format("%.4f", event.values[1]));
                    tvAccelZ.setText(String.format("%.4f", event.values[2]));
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    event = (SensorEvent) msg.obj;
                    tvGyroX.setText(String.format("%.4f", event.values[0]));
                    tvGyroY.setText(String.format("%.4f", event.values[1]));
                    tvGyroZ.setText(String.format("%.4f", event.values[2]));
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    event = (SensorEvent) msg.obj;
                    tvMagX.setText(String.format("%.4f", event.values[0]));
                    tvMagY.setText(String.format("%.4f", event.values[1]));
                    tvMagZ.setText(String.format("%.4f", event.values[2]));
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

        formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        mRecordingDir = getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();

        initServiceConnection();
    }

    private void initImuView() {
        tvAccelX = (TextView) findViewById(R.id.tvAcceX);
        tvAccelY = (TextView) findViewById(R.id.tvAcceY);
        tvAccelZ = (TextView) findViewById(R.id.tvAcceZ);
        tvGyroX = (TextView) findViewById(R.id.tvGyroX);
        tvGyroY = (TextView) findViewById(R.id.tvGyroY);
        tvGyroZ = (TextView) findViewById(R.id.tvGyroZ);
        tvMagX = (TextView) findViewById(R.id.tvMagX);
        tvMagY = (TextView) findViewById(R.id.tvMagY);
        tvMagZ = (TextView) findViewById(R.id.tvMagZ);
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
        Intent locationIntent = new Intent(CollectionActivity.this, LocationService.class);
        bindService(locationIntent, mLocationServiceConnection, BIND_AUTO_CREATE);
        // 绑定IMU服务
        Intent imuIntent = new Intent(CollectionActivity.this, ImuService.class);
        bindService(imuIntent, mImuServiceConnection, BIND_AUTO_CREATE);

        btnCollectData.setText("Stop");
    }

    private void unbindServices() {
        // 服务解绑
        unbindService(mLocationServiceConnection);
        unbindService(mImuServiceConnection);

        btnCollectData.setText("Collect");
        setDefaultImuInfo();
        setDefaultGnssInfo();
    }

    private void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnCollectData:
                if (btnCollectData.getText().equals("Collect")) {
                    bindServices();
                } else {
                    unbindServices();
                }
                break;
            default:
                break;
        }
    }

    private void initServiceConnection() {
        // 数据存储路径
        String recordingDir = mRecordingDir + File.separator + formatter.format(new Date(System.currentTimeMillis()));

        mLocationServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "onServiceConnected location");
                mLocationBinder = (LocationService.MyBinder) service;
                mLocationService = mLocationBinder.getLocationService();

                mLocationService.startLocationRecording(recordingDir);

                mLocationService.setCallback(new LocationService.Callback() {
                    @Override
                    public void onLocationChanged(Location location) {
                        Log.d(TAG, "onLocationChanged");
                        Message msg = new Message();
                        msg.what = GNSS_LOCATION_UPDATE_CODE;
                        msg.obj = LocationUtils.genLocationMap(location);
                        CollectionActivity.this.getHandler().sendMessage(msg);
                    }

                    @Override
                    public void onLocationProvoiderDisabled() {
                        Message msg = Message.obtain();
                        msg.what = GNSS_PROVIDER_DISABLED_CODE;
                        CollectionActivity.this.getHandler().sendMessage(msg);

                        unbindServices();
                    }

                    @Override
                    public void onLocationSearching(String data) {
                        Log.d(TAG, "onLocationSearching");
                        Message msg = Message.obtain();
                        msg.what = GNSS_SEARCHING_CODE;
                        msg.obj = data;
                        CollectionActivity.this.getHandler().sendMessage(msg);
                    }
                });
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };

        mImuServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "onServiceConnected IMU");
                mImuBinder = (ImuService.MyBinder) service;
                mImuService = mImuBinder.getImuService();

                mImuService.startImuRecording(recordingDir);

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
                        CollectionActivity.this.getHandler().sendMessage(msg);
                    }
                });
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };
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
        tvMagX.setText("--");
        tvMagY.setText("--");
        tvMagZ.setText("--");
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
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        if (btnCollectData.getText().equals("Stop")) {
            unbindServices();
        }
    }
}