package com.zhushuli.recordipin.fragments;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.Sensor;
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
import android.widget.ImageButton;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.zhushuli.recordipin.R;
import com.zhushuli.recordipin.models.imu.ImuInfo;
import com.zhushuli.recordipin.services.ImuService2;
import com.zhushuli.recordipin.services.LocationService2;
import com.zhushuli.recordipin.utils.DialogUtils;
import com.zhushuli.recordipin.utils.ThreadUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author      : zhushuli
 * @createDate  : 2023/07/06 15:29
 * @description : Smartphone-based Visual-Inertial-GNSS Dataset
 */
public class VIGDFragment extends Fragment {

    private static final String TAG = "VIGDFragment";

    private TextView tvGnssInfo;

    private TextView tvImuInfo;

    private ImageButton btnRecord;

    private final AtomicBoolean recording = new AtomicBoolean(false);

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
    private final SimpleDateFormat storageFormatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");

    private final SimpleDateFormat displayFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private String mRecordRootDir;

    private String mRecordAbsDir;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case LocationService2.GNSS_LOCATION_CHANGED_CODE:
                    Location location = (Location) msg.obj;
                    StringBuffer sb = new StringBuffer();
                    sb.append(displayFormatter.format(new Date(System.currentTimeMillis()))).append("\n");
                    sb.append(String.format("%.6f,%.6f,%.2fm",
                            location.getLongitude(), location.getLatitude(), location.getAccuracy())).append("\n");
                    sb.append(String.format("%.2fm/s,%.2f",
                            location.getSpeed(), location.getBearing()));
                    tvGnssInfo.setText(sb.toString());
                    break;
                case LocationService2.GNSS_PROVIDER_DISABLED_CODE:
                    DialogUtils.showLocationSettingsAlert(getActivity());
                    clickToggleRecording(null);
                    break;
                case Sensor.TYPE_ACCELEROMETER:
                    ImuInfo accelInfo = (ImuInfo) msg.obj;
                    tvImuInfo.setText(String.format("%.4f,%.4f,%.4f",
                            accelInfo.values[0], accelInfo.values[1], accelInfo.values[2]));
                    break;
                default:
                    break;
            }
        }
    };

    public VIGDFragment() {
        // Required empty public constructor
    }

    public static VIGDFragment newInstance() {
        VIGDFragment fragment = new VIGDFragment();
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
        return inflater.inflate(R.layout.fragment_vigd, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvGnssInfo = (TextView) view.findViewById(R.id.gnssInfo);
        tvImuInfo = (TextView) view.findViewById(R.id.imuInfo);
        btnRecord = (ImageButton) view.findViewById(R.id.btnRecord);
        btnRecord.setOnClickListener(this::clickToggleRecording);
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

        ThreadUtils.interrupt(mLocationReceiveThread);
        ThreadUtils.interrupt(mSatelliteReceiverThread);
        ThreadUtils.interrupt(mImuReceiverThread);
    }

    private void clickToggleRecording(@SuppressWarnings("unused") View unused) {
        Log.d(TAG, "clickToggleRecording");
        recording.set(!recording.get());
        if (recording.get()) {
            btnRecord.setImageResource(R.drawable.baseline_stop_24);
            mRecordAbsDir = mRecordRootDir + File.separator + storageFormatter.format(new Date(System.currentTimeMillis()));
            bindServices();
        } else {
            unbindServices();
            btnRecord.setImageResource(R.drawable.baseline_fiber_manual_record_24);
            tvGnssInfo.setText("GNSS Information...");
            tvImuInfo.setText("IMU Information...");
        }
    }

    private void bindServices() {
        // 绑定定位服务
        Intent locationIntent = new Intent(getActivity(), LocationService2.class);
        getActivity().bindService(locationIntent, mLocationServiceConn, Context.BIND_AUTO_CREATE);
        // 绑定IMU服务
        Intent imuIntent = new Intent(getActivity(), ImuService2.class);
        getActivity().bindService(imuIntent, mImuServiceConn, Context.BIND_AUTO_CREATE);
    }

    private void unbindServices() {
        // 服务解绑
        getActivity().unbindService(mLocationServiceConn);
        getActivity().unbindService(mImuServiceConn);
    }
}