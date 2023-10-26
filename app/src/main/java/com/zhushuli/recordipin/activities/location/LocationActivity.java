package com.zhushuli.recordipin.activities.location;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.GnssStatus;
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

import com.zhushuli.recordipin.R;
import com.zhushuli.recordipin.models.location.SatelliteInfo;
import com.zhushuli.recordipin.services.LocationService2;
import com.zhushuli.recordipin.utils.DialogUtils;
import com.zhushuli.recordipin.utils.ThreadUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

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
    private Button btnLocServiceStart;

    private LocationService2.LocationBinder binder;

    private LocationService2 mLocationService2;

    // 时间戳转日期
    private final SimpleDateFormat storageFormatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

    private final SimpleDateFormat displayFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // 数据存储路径
    private String mRecordRootDir;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case LocationService2.GNSS_LOCATION_CHANGED_CODE:
                    tvCoordinate.setText("WGS84");
//                    HashMap<String, String> map = (HashMap<String, String>) msg.obj;
//                    tvDate.setText(map.get("date"));
//                    tvGnssTime.setText(map.get("time"));
//                    tvLocation.setText(map.get("location"));
//                    tvLocationAcc.setText(map.get("accuracy"));
//                    tvSpeed.setText(map.get("speed"));
//                    tvBearing.setText(map.get("bearing"));
//                    tvAltitude.setText(map.get("altitude"));
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
                    setDefaultGnssInfo();
                    DialogUtils.showLocationSettingsAlert(LocationActivity.this);
                    try {
                        unbindService(mLocationServiceConn);
                    } catch (IllegalArgumentException e) {
                        // Nothing to do
                    }
                    btnLocServiceStart.setText("Start");
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
            Log.d(TAG, action);

            Message msg = Message.obtain();
            if (action.equals(LocationService2.GNSS_LOCATION_CHANGED_ACTION)) {
                Location location = intent.getParcelableExtra("Location");
                msg.what = LocationService2.GNSS_LOCATION_CHANGED_CODE;
                msg.obj = location;
                mMainHandler.sendMessage(msg);
            } else if (action.equals(LocationService2.GNSS_PROVIDER_DISABLED_ACTION)) {
                mMainHandler.sendEmptyMessage(LocationService2.GNSS_PROVIDER_DISABLED_CODE);
            }
        }
    };

    private final HandlerThread mLocationReceiverThread = new HandlerThread("Location Receiver");

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

    private final ServiceConnection mLocationServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            binder = (LocationService2.LocationBinder) service;
            mLocationService2 = binder.getLocationService2();

            String recordDir = mRecordRootDir + File.separator + storageFormatter.format(new Date(System.currentTimeMillis()));
            mLocationService2.startLocationRecord(recordDir);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
        }
    };

    private final View.OnClickListener mGraphListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.tvSatellite:
                    Intent skyViewIntent = new Intent(LocationActivity.this, GnssSkyViewActivity.class);
                    startActivity(skyViewIntent);
                    break;
            }
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

        btnLocServiceStart = (Button) findViewById(R.id.btnLocServiceStart);
        btnLocServiceStart.setOnClickListener(this);

        mLocationReceiverThread.start();
        mSatelliteReceiverThread.start();

        mRecordRootDir = getExternalFilesDir(Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();

        tvSatellite.setOnClickListener(mGraphListener);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnLocServiceStart:
                if (btnLocServiceStart.getText().equals("Start")) {
                    Intent intent = new Intent(LocationActivity.this, LocationService2.class);
                    bindService(intent, mLocationServiceConn, BIND_AUTO_CREATE);

                    btnLocServiceStart.setText("Stop");
                } else {
                    unbindService(mLocationServiceConn);
                    setDefaultGnssInfo();

                    btnLocServiceStart.setText("Start");
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

        IntentFilter locationIntent = new IntentFilter();
        locationIntent.addAction(LocationService2.GNSS_LOCATION_CHANGED_ACTION);
        locationIntent.addAction(LocationService2.GNSS_PROVIDER_DISABLED_ACTION);
        registerReceiver(mLocationReceiver, locationIntent, null, new Handler(mLocationReceiverThread.getLooper()));

        IntentFilter satelliteIntent = new IntentFilter();
        satelliteIntent.addAction(LocationService2.GNSS_SATELLITE_STATUS_CHANGED_ACTION);
        registerReceiver(mSatelliteReceiver, satelliteIntent, null, new Handler(mSatelliteReceiverThread.getLooper()));
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
        unregisterReceiver(mSatelliteReceiver);
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
        mLocationReceiverThread.quitSafely();
        mSatelliteReceiverThread.quitSafely();
    }
}