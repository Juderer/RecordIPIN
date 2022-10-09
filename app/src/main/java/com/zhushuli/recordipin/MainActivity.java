package com.zhushuli.recordipin;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "My" + MainActivity.class.getSimpleName();

    private static final int MY_PERMISSION_REQUEST_CODE = 2024;
    private static final int GPS_LOC_UPDATE_CODE = 8402;

    private final String[] permissions = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION};

    private TextView locText;
    private long mExitTime;

    private GPSCollecionThread gpsCollectionThread;

    public static boolean continueCollection = true;

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case GPS_LOC_UPDATE_CODE:
                    String locStr = (String) msg.obj;
                    locText.setText(locStr);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate");

        locText = (TextView) findViewById(R.id.locText);

        Button btn2ThreadActivity = (Button) findViewById(R.id.btn2TheardActivity);
        btn2ThreadActivity.setOnClickListener(this);

        Button gpsTestBtn = (Button) findViewById(R.id.gpsTestBtn);
        gpsTestBtn.setOnClickListener(this);

        Button gpsCollectBtn = (Button) findViewById(R.id.gpsCollectBtn);
        gpsCollectBtn.setOnClickListener(this);

        Button gpsStopBtn = (Button) findViewById(R.id.gpsStopBtn);
        gpsStopBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn2TheardActivity:
                Intent intent = new Intent(this, ThreadTestActivity.class);
                startActivity(intent);
                break;
            case R.id.gpsTestBtn:
                boolean isAllGranted = checkPermissionAllGranted(permissions);
                if (!isAllGranted) {
                    Log.d(TAG, "定位未授权");
                    ActivityCompat.requestPermissions(MainActivity.this, permissions, MY_PERMISSION_REQUEST_CODE);
                } else {
                    Toast.makeText(this, "GPS正常", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.gpsCollectBtn:
                continueCollection = true;
                gpsCollectionThread = new GPSCollecionThread(MainActivity.this, getHandler());
                Thread testThread = new Thread(gpsCollectionThread);
                testThread.start();
                break;
            case R.id.gpsStopBtn:
                continueCollection = false;
                break;
            default:
                break;
        }
    }

    private boolean checkPermissionAllGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(MainActivity.this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSION_REQUEST_CODE) {
            boolean isAllGranted = true;
            for (int grant : grantResults) {
                if (grant != PackageManager.PERMISSION_GRANTED) {
                    isAllGranted = false;
                    break;
                }
            }
            if (isAllGranted) {
                Log.d(TAG, "用户已授权");
            } else {
                Log.d(TAG, "用户未授权");
                openAppDetails();
            }
        }
    }

    private void openAppDetails() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("RecordIPI需要定位权限，请到\"应用信息->权限\"中授予！");
        builder.setPositiveButton("去手动授权", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                startActivity(intent);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    @SuppressLint("HandlerLeak")
    private class GPSCollecionThread implements Runnable {
        private Activity activity;
        private LocationManager mLocationManager;
        private Location mLocation = null;
        private Handler mMainHandler;
        private int sleepDuration = 1000;

        private LocationListener mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                mLocation = location;
                Log.d(TAG, "onLocationChanged: " + String.valueOf(mLocation.getLongitude()) + "," + String.valueOf(mLocation.getLatitude()));
            }
        };

        public GPSCollecionThread(Activity activity, Handler mMainHandler) {
            this.activity = activity;
            this.mMainHandler = mMainHandler;
            mLocationManager = (LocationManager) this.activity.getSystemService(Context.LOCATION_SERVICE);
            initLocation();
        }

        @SuppressLint("MissingPermission")
        public void initLocation() {
            mLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Log.d(TAG, "mLocation: " + String.valueOf(mLocation.getLongitude()) + "," + String.valueOf(mLocation.getLatitude()));
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, mLocationListener);
        }

        @Override
        public void run() {
            Looper.prepare();
            Log.d(TAG, "GPSCollectionThread Start");
            while (MainActivity.continueCollection) {
                if (mLocation != null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(mLocation.getLongitude());
                    sb.append(";");
                    sb.append(mLocation.getLatitude());
                    Log.d(TAG, sb.toString());

                    Message mMessage = Message.obtain();
                    mMessage.obj = sb.toString();
                    mMessage.what = GPS_LOC_UPDATE_CODE;
                    mMainHandler.sendMessage(mMessage);
                }
                try {
                    Thread.sleep(sleepDuration);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Log.d(TAG, "GPSCollectionThread End");
        }

        public Handler getHandler() {
            return mHandler;
        }
    }

    public Handler getHandler() {
        return mHandler;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if ((System.currentTimeMillis() - mExitTime) > 5000) {
                Toast.makeText(this, "再按一次退出RecordIPIN", Toast.LENGTH_SHORT).show();
                mExitTime = System.currentTimeMillis();
            } else {
                finish();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
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
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestory");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }
}