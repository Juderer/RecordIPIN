package com.zhushuli.recordipin;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.text.DecimalFormat;

public class LocationActivity extends AppCompatActivity {

    private static final String TAG = "My" + LocationActivity.class.getSimpleName();

    private static final int GNSS_LOCATION_UPDATE_CODE = 8402;
    private static final int GNSS_SEARCHING_CODE = 502;
    private static final int GNSS_PROVIDER_DISABLED_CODE = 404;

    private TextView tvLocationMsg;
    private Button btnLocationStart;
    private Button btnLocationStop;

    private LocationThread mLocationThread;
    public static boolean isLocationThreadStart = false;

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
                    isLocationThreadStart = false;
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

        tvLocationMsg = (TextView) findViewById(R.id.tvLocationMsg);
        btnLocationStart = (Button) findViewById(R.id.btnLocationStart);
        btnLocationStart.setOnClickListener(this::onClick);
        btnLocationStop = (Button) findViewById(R.id.btnLocationStop);
        btnLocationStop.setOnClickListener(this::onClick);
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnLocationStart:
                if (isLocationThreadStart) {
                    break;
                }
                isLocationThreadStart = true;
                mLocationThread = new LocationThread(LocationActivity.this, getHandler());
                new Thread(mLocationThread).start();
                break;
            case R.id.btnLocationStop:
                if (!isLocationThreadStart) {
                    break;
                }
                isLocationThreadStart = false;
                tvLocationMsg.setText("Location Stop");
                break;
            default:
                break;
        }
    }

    @SuppressLint("HandlerLeak")
    private class LocationThread implements Runnable {
        private Activity mActivity;
        private LocationManager mLocationManager;
        private Location mLocation;
        private Handler mMainHandler;
        private int sleepDuration = 1000;
        private boolean checkGPS = false;
        private Message mMessage;
        private DecimalFormat dfLon;
        private DecimalFormat dfSpd;

        private LocationListener mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                mLocation = location;
//                Log.d(TAG, "onLocationChanged: " +
//                        String.valueOf(mLocation.getLongitude()) + "," + String.valueOf(mLocation.getLatitude()));
            }

            @Override
            public void onProviderDisabled(@NonNull String provider) {
                Log.d(TAG, "onProviderDisabled");
                checkGPS = false;
                DialogUtils.showLocationSettingsAlert(mActivity);
                if (mLocationManager != null) {
                    checkGPS = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                }
                mLocation = null;
            }
        };

        public LocationThread(Activity mActivity, Handler mMainHandler) {
            this.mActivity = mActivity;
            this.mMainHandler = mMainHandler;
            mLocationManager = (LocationManager) this.mActivity.getSystemService(Context.LOCATION_SERVICE);
            checkGPS = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            Log.d(TAG, "checkGPS " + String.valueOf(checkGPS));
            if (!checkGPS) {
                DialogUtils.showLocationSettingsAlert(mActivity);
            }
            initLocation();

            dfLon = new DecimalFormat("#.000000");  // (经纬度)保留小数点后六位
            dfSpd = new DecimalFormat("#0.00");  // (速度或航向)保留小数点后两位
        }

        @SuppressLint("MissingPermission")
        public void initLocation() {
            mLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, mLocationListener);
        }

        public void stopLocction() {
            if (mLocationManager != null) {
                mLocationManager.removeUpdates(mLocationListener);
                mLocationManager = null;
            }
        }

        public String getLocationMsg() {
            StringBuilder sb = new StringBuilder();
            sb.append("Longitude:\t");
            sb.append(dfLon.format(mLocation.getLongitude()));
            sb.append("\nLatitude:\t");
            sb.append(dfLon.format(mLocation.getLatitude()));
            sb.append("\nAccuracy:\t");
            sb.append((int) mLocation.getAccuracy());
            sb.append("\nSpeed:\t");
            sb.append(dfSpd.format(mLocation.getSpeed()));
            sb.append("\nBearing:\t");
            sb.append(dfSpd.format(mLocation.getBearing()));
            return sb.toString();
        }

        @Override
        public void run() {
            Log.d(TAG, "LocationThread Start");
            while (LocationActivity.isLocationThreadStart) {
                if (!checkGPS) {
                    mMessage = Message.obtain();
                    mMessage.what = GNSS_PROVIDER_DISABLED_CODE;
                    mMainHandler.sendMessage(mMessage);
                    break;
                }
                if (mLocation != null) {
                    String locMsg = getLocationMsg();
//                    Log.d(TAG, locMsg);

                    mMessage = Message.obtain();
                    mMessage.obj = locMsg;
                    mMessage.what = GNSS_LOCATION_UPDATE_CODE;
                    mMainHandler.sendMessage(mMessage);
                } else {
                    mMessage = Message.obtain();
                    mMessage.what = GNSS_SEARCHING_CODE;
                    mMainHandler.sendMessage(mMessage);
                }
                try {
                    Thread.sleep(sleepDuration);
//                    Log.d(TAG, "Thread Sleep");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            stopLocction();
            Log.d(TAG, "LocationThread End");
        }
    }

    public Handler getHandler() {
        return mHandler;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        isLocationThreadStart = false;
    }
}