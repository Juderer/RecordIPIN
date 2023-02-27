package com.zhushuli.recordipin;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
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

import com.alibaba.fastjson.JSON;
import com.zhushuli.recordipin.services.LocationService2;
import com.zhushuli.recordipin.utils.DialogUtils;
import com.zhushuli.recordipin.utils.ThreadUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocationActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = LocationActivity.class.getSimpleName();
//    private static final String TAG = LocationActivity.class.getName();

    private TextView tvDate;
    private TextView tvCoordinate;
    private TextView tvSatellite;
    private TextView tvGnssTime;
    private TextView tvLocation;
    private TextView tvLocationAcc;
    private TextView tvSpeed;
    private TextView tvBearing;
    private TextView tvAltitude;
    private CheckBox cbRecord;
    private Button btnLocServiceStart;

    private LocationService2.MyBinder binder;

    // 时间戳转日期
    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
    // 数据存储路径
    private String mRecordRootDir;

    private AtomicBoolean recording = new AtomicBoolean(false);

    private final Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case LocationService2.GNSS_LOCATION_CHANGED_CODE:
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
                case LocationService2.GNSS_SATELLITE_STATUS_CHANGED_CODE:
                    tvSatellite.setText((String) msg.obj);
                    break;
                case LocationService2.GNSS_PROVIDER_DISABLED_CODE:
                    setDefaultGnssInfo();
                    DialogUtils.showLocationSettingsAlert(LocationActivity.this);
                    break;
                default:
                    break;
            }
        }
    };

    private final BroadcastReceiver mLocationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive:" + ThreadUtils.threadID());
            String action = intent.getAction();
            Message msg = Message.obtain();
            if (action.equals(LocationService2.GNSS_SATELLITE_STATUS_CHANGED_ACTION)) {
                msg.what = LocationService2.GNSS_SATELLITE_STATUS_CHANGED_CODE;
                msg.obj = intent.getStringExtra("satellite");
                mMainHandler.sendMessage(msg);
            } else if (action.equals(LocationService2.GNSS_LOCATION_CHANGED_ACTION)) {
                String location = intent.getStringExtra("location");
                msg.what = LocationService2.GNSS_LOCATION_CHANGED_CODE;
                msg.obj = JSON.parseObject(location, HashMap.class);
                mMainHandler.sendMessage(msg);
            } else if (action.equals(LocationService2.GNSS_PROVIDER_DISABLED_ACTION)) {
                mMainHandler.sendEmptyMessage(LocationService2.GNSS_PROVIDER_DISABLED_CODE);
            }
        }
    };

    private final HandlerThread mReceiverThread = new HandlerThread("Receive");

    private final ServiceConnection mLocationServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            binder = (LocationService2.MyBinder) service;
            LocationService2 mLocationService2 = binder.getLocationService2();

            if (recording.get()) {
                // 数据存储路径
                String recordDir = mRecordRootDir + File.separator + formatter.format(new Date(System.currentTimeMillis()));
                mLocationService2.startLocationRecord(recordDir);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);
        Log.d(TAG, "onCreate:" + ThreadUtils.threadID());

        tvDate = (TextView) findViewById(R.id.tvDate);
        tvCoordinate = (TextView) findViewById(R.id.tvCoordinate);
        tvSatellite = (TextView) findViewById(R.id.tvSatellite);
        tvGnssTime = (TextView) findViewById(R.id.tvGnssTime);
        tvLocation = (TextView) findViewById(R.id.tvLocation);
        tvLocationAcc = (TextView) findViewById(R.id.tvLocationAcc);
        tvSpeed = (TextView) findViewById(R.id.tvSpeed);
        tvBearing = (TextView) findViewById(R.id.tvBearing);
        tvAltitude = (TextView) findViewById(R.id.tvAltitude);

        cbRecord = (CheckBox) findViewById(R.id.cbRecord);
        cbRecord.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    recording.set(true);
                } else {
                    recording.set(false);
                }
            }
        });

        btnLocServiceStart = (Button) findViewById(R.id.btnLocServiceStart);
        btnLocServiceStart.setOnClickListener(this);

        mReceiverThread.start();

        mRecordRootDir = getExternalFilesDir(Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnLocServiceStart:
                if (btnLocServiceStart.getText().equals("Start")) {
                    Intent intent = new Intent(LocationActivity.this, LocationService2.class);
                    bindService(intent, mLocationServiceConn, BIND_AUTO_CREATE);

                    btnLocServiceStart.setText("Stop");
                    cbRecord.setEnabled(false);
                } else {
                    unbindService(mLocationServiceConn);
                    setDefaultGnssInfo();

                    btnLocServiceStart.setText("Start");
                    cbRecord.setEnabled(true);
                }
                break;
            default:
                break;
        }
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

        IntentFilter intent = new IntentFilter();
        intent.addAction(LocationService2.GNSS_SATELLITE_STATUS_CHANGED_ACTION);
        intent.addAction(LocationService2.GNSS_LOCATION_CHANGED_ACTION);
        intent.addAction(LocationService2.GNSS_PROVIDER_DISABLED_ACTION);
        registerReceiver(mLocationReceiver, intent, null, new Handler(mReceiverThread.getLooper()));
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

        unregisterReceiver(mLocationReceiver);
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

        if (btnLocServiceStart.getText().equals("Stop")) {
            unbindService(mLocationServiceConn);
        }
        mReceiverThread.quitSafely();
    }
}