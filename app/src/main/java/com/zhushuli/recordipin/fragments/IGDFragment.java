package com.zhushuli.recordipin.fragments;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.location.GnssStatus;
import android.location.Location;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.zhushuli.recordipin.R;
import com.zhushuli.recordipin.models.imu.ImuInfo;
import com.zhushuli.recordipin.models.location.SatelliteInfo;
import com.zhushuli.recordipin.services.ImuService2;
import com.zhushuli.recordipin.services.LocationService2;
import com.zhushuli.recordipin.utils.DialogUtils;
import com.zhushuli.recordipin.utils.ThreadUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author      : zhushuli
 * @createDate  : 2023/07/06 11:42
 * @description : Smartphone-based Inertial-GNSS Dataset
 */
public class IGDFragment extends Fragment {

    private static final String TAG = "IGDFragment";

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
            Log.d(TAG, "onReceive Satellite");
            String action = intent.getAction();
            Log.d(TAG, action);

            if (action.equals(LocationService2.GNSS_SATELLITE_STATUS_CHANGED_ACTION)) {
                List<SatelliteInfo> satellites = intent.getParcelableArrayListExtra("Satellites");

                int beidouSatelliteCount = 0;
                int gpsSatelliteCount = 0;
                for (SatelliteInfo satellite : satellites) {
                    if (satellite.isUsed()) {
                        switch (satellite.getConstellationType()) {
                            case GnssStatus.CONSTELLATION_BEIDOU:
                                beidouSatelliteCount += 1;
                                break;
                            case GnssStatus.CONSTELLATION_GPS:
                                gpsSatelliteCount += 1;
                                break;
                            default:
                                break;
                        }
                    }
                }

                Message msg = Message.obtain();
                msg.what = LocationService2.GNSS_SATELLITE_STATUS_CHANGED_CODE;
                msg.obj = String.format("%02d Beidou; %02d GPS", beidouSatelliteCount, gpsSatelliteCount);
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
    private final SimpleDateFormat storageFormatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");

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
                    DialogUtils.showLocationSettingsAlert(getActivity());
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

    public IGDFragment() {
        // Required empty public constructor
    }

    public static IGDFragment newInstance() {
        Log.d(TAG, "newInstance");
        IGDFragment fragment = new IGDFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        if (getActivity() == null) {
            onDestroy();
        }

        mRecordRootDir = getActivity().getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();

        mLocationReceiveThread.start();
        mSatelliteReceiverThread.start();
        mImuReceiverThread.start();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_igd, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onViewCreated");

        getView().setBackgroundColor(0xFFFDF5E6);

        initImuView(view);
        initGnssView(view);
        btnCollectData = (Button) view.findViewById(R.id.btnCollectData);
        btnCollectData.setOnClickListener(this::onClick);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        IntentFilter locationIntent = new IntentFilter();
        locationIntent.addAction(LocationService2.GNSS_LOCATION_CHANGED_ACTION);
        locationIntent.addAction(LocationService2.GNSS_PROVIDER_DISABLED_ACTION);
        getActivity().registerReceiver(
                mLocationReceiver,
                locationIntent,
                null,
                new Handler(mLocationReceiveThread.getLooper()));

        IntentFilter satelliteIntent = new IntentFilter();
        satelliteIntent.addAction(LocationService2.GNSS_SATELLITE_STATUS_CHANGED_ACTION);
        getActivity().registerReceiver(
                mSatelliteReceiver,
                satelliteIntent,
                null,
                new Handler(mSatelliteReceiverThread.getLooper()));

        IntentFilter imuIntent = new IntentFilter();
        imuIntent.addAction(ImuService2.IMU_SENSOR_CHANGED_ACTION);
        getActivity().registerReceiver(
                mImuReceiver,
                imuIntent,
                null,
                new Handler(mImuReceiverThread.getLooper()));
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");

        getActivity().unregisterReceiver(mLocationReceiver);
        getActivity().unregisterReceiver(mSatelliteReceiver);
        getActivity().unregisterReceiver(mImuReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        if (btnCollectData.getText().equals("Stop")) {
            unbindServices();
        }

        ThreadUtils.interrupt(mLocationReceiveThread);
        ThreadUtils.interrupt(mSatelliteReceiverThread);
        ThreadUtils.interrupt(mImuReceiverThread);
    }

    private void initImuView(View view) {
        tvAccelX = (TextView) view.findViewById(R.id.tvAccelX);
        tvAccelY = (TextView) view.findViewById(R.id.tvAccelY);
        tvAccelZ = (TextView) view.findViewById(R.id.tvAccelZ);
        tvGyroX = (TextView) view.findViewById(R.id.tvGyroX);
        tvGyroY = (TextView) view.findViewById(R.id.tvGyroY);
        tvGyroZ = (TextView) view.findViewById(R.id.tvGyroZ);
    }

    private void initGnssView(View view) {
        tvDate = (TextView) view.findViewById(R.id.tvDate);
        tvCoordinate = (TextView) view.findViewById(R.id.tvCoordinate);
        tvSatellite = (TextView) view.findViewById(R.id.tvSatellite);
        tvGnssTime = (TextView) view.findViewById(R.id.tvGnssTime);
        tvLocation = (TextView) view.findViewById(R.id.tvLocation);
        tvLocationAcc = (TextView) view.findViewById(R.id.tvLocationAcc);
        tvSpeed = (TextView) view.findViewById(R.id.tvSpeed);
        tvBearing = (TextView) view.findViewById(R.id.tvBearing);
        tvAltitude = (TextView) view.findViewById(R.id.tvAltitude);
    }

    private void bindServices() {
        // 绑定定位服务
        Intent locationIntent = new Intent(getActivity(), LocationService2.class);
        getActivity().bindService(locationIntent, mLocationServiceConn, Context.BIND_AUTO_CREATE);
        // 绑定IMU服务
        Intent imuIntent = new Intent(getActivity(), ImuService2.class);
        getActivity().bindService(imuIntent, mImuServiceConn, Context.BIND_AUTO_CREATE);

        btnCollectData.setText("Stop");
    }

    private void unbindServices() {
        // 服务解绑
        getActivity().unbindService(mLocationServiceConn);
        getActivity().unbindService(mImuServiceConn);

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
}