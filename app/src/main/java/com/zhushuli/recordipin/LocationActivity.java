package com.zhushuli.recordipin;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class LocationActivity extends AppCompatActivity implements ServiceConnection {

    private static final String TAG = "My" + LocationActivity.class.getSimpleName();

    private static final int GNSS_LOCATION_UPDATE_CODE = 8402;
    private static final int GNSS_SEARCHING_CODE = 502;
    private static final int GNSS_PROVIDER_DISABLED_CODE = 404;

    private TextView tvLocationMsg;
    private Button btnLocServiceStart;
    private Button btnLocServiceStop;

    private LocationService.MyBinder binder = null;

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case GNSS_LOCATION_UPDATE_CODE:
                    String mLocationMsg = (String) msg.obj;
                    tvLocationMsg.setText(mLocationMsg);
                    break;
                case GNSS_SEARCHING_CODE:
                    tvLocationMsg.setText("GNSS Searching ...");
                    break;
                case GNSS_PROVIDER_DISABLED_CODE:
                    tvLocationMsg.setText("GNSS Provider Disabled");
                    DialogUtils.showLocationSettingsAlert(LocationActivity.this);
                    break;
                default:
                    tvLocationMsg.setText((String) msg.obj);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);
        Log.d(TAG, "onCreate");

        tvLocationMsg = (TextView) findViewById(R.id.tvLocationMsg);
        btnLocServiceStart = (Button) findViewById(R.id.btnLocServiceStart);
        btnLocServiceStart.setOnClickListener(this::onClick);
        btnLocServiceStop = (Button) findViewById(R.id.btnLocServiceStop);
        btnLocServiceStop.setOnClickListener(this::onClick);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        binder = (LocationService.MyBinder) service;
        LocationService mLocationService = binder.getLocationService();
        mLocationService.setCallback(new LocationService.Callback() {
            @Override
            public void onLocationChange(String data) {
                Message msg = new Message();
                msg.obj = data;
                mHandler.sendMessage(msg);
            }

            @Override
            public void onLocationProvoiderDisabled() {
                Message msg = Message.obtain();
                msg.what = GNSS_PROVIDER_DISABLED_CODE;
                mHandler.sendMessage(msg);

                unbindService(LocationActivity.this);
                binder = null;
            }

            @Override
            public void onLocationSearching() {
                Message msg = Message.obtain();
                msg.what = GNSS_SEARCHING_CODE;
                mHandler.sendMessage(msg);
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnLocServiceStart:
                Intent intent = new Intent(LocationActivity.this, LocationService.class);
                bindService(intent, LocationActivity.this, BIND_AUTO_CREATE);
                break;
            case R.id.btnLocServiceStop:
                if (binder != null) {
                    unbindService(LocationActivity.this);
                    binder = null;
                    tvLocationMsg.setText("Location Stop");
                }
                break;
            default:
                break;
        }
    }

    public Handler getHandler() {
        return mHandler;
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
    }
}