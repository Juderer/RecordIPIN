package com.zhushuli.recordipin;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
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

import com.zhushuli.recordipin.service.LocationService;
import com.zhushuli.recordipin.utils.DialogUtils;
import com.zhushuli.recordipin.utils.LocationUtils;

import org.w3c.dom.Text;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;


public class LocationActivity extends AppCompatActivity implements ServiceConnection, View.OnClickListener {

    private static final String TAG = "My" + LocationActivity.class.getSimpleName();
//    private static final String TAG = "My" + LocationActivity.class.getName();

    private static final int GNSS_LOCATION_UPDATE_CODE = 8402;
    private static final int GNSS_SEARCHING_CODE = 502;
    private static final int GNSS_PROVIDER_DISABLED_CODE = 404;

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

    private LocationService.MyBinder binder;

    // 数据存储路径
    private SimpleDateFormat formatter;
    private String mRecordingDir;

    private Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
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
                    setDefaultGnssInfo();
                    DialogUtils.showLocationSettingsAlert(LocationActivity.this);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);
        Log.d(TAG, "onCreate");

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

        formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        mRecordingDir = getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(TAG, "onServiceConnected");
        binder = (LocationService.MyBinder) service;
        LocationService mLocationService = binder.getLocationService();

        // 数据存储路径
        String recordingDir = mRecordingDir + File.separator + formatter.format(new Date(System.currentTimeMillis()));
        mLocationService.startLocationRecording(recordingDir);

        mLocationService.setCallback(new LocationService.Callback() {
            @Override
            public void onLocationChanged(Location location) {
                Log.d(TAG, "onLocationChanged");
                Message msg = new Message();
                msg.what = GNSS_LOCATION_UPDATE_CODE;
                msg.obj = LocationUtils.genLocationMap(location);
                mMainHandler.sendMessage(msg);
            }

            @Override
            public void onLocationProvoiderDisabled() {
                Message msg = Message.obtain();
                msg.what = GNSS_PROVIDER_DISABLED_CODE;
                mMainHandler.sendMessage(msg);

                unbindService(LocationActivity.this);
                binder = null;
                btnLocServiceStart.setText("Start");
            }

            @Override
            public void onLocationSearching(String data) {
                Log.d(TAG, "onLocationSearching");
                Message msg = Message.obtain();
                msg.what = GNSS_SEARCHING_CODE;
                msg.obj = data;
                mMainHandler.sendMessage(msg);
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.d(TAG, "onServiceDisconnected");
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnLocServiceStart:
                if (btnLocServiceStart.getText().equals("Start")) {
                    Intent intent = new Intent(LocationActivity.this, LocationService.class);
                    bindService(intent, LocationActivity.this, BIND_AUTO_CREATE);

                    btnLocServiceStart.setText("Stop");
                } else {
                    unbindService(LocationActivity.this);
                    setDefaultGnssInfo();

                    btnLocServiceStart.setText("Start");
                }
                break;
            default:
                break;
        }
    }

    public Handler getMainHandler() {
        return mMainHandler;
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
        Log.d(TAG, "onDestory");

        if (btnLocServiceStart.getText().equals("Stop")) {
            unbindService(LocationActivity.this);
        }
    }
}