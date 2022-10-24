package com.zhushuli.recordipin;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;


import androidx.annotation.NonNull;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LocationService extends Service {

    private static final String TAG = "My" + LocationService.class.getSimpleName();

    private boolean isConnecting;
    private Callback callback;

    private LocationManager mLocationManager;
    private LocationListener mLocationListener;
    private GnssStatus.Callback mGnssStatusCallback;
    private Location mLocation;
    private int mSatelliteCount;
    private int mBeidouSatelliteCount;
    private int mGpsSatelliteCount;

    private DecimalFormat dfLon;
    private DecimalFormat dfSpd;
    private SimpleDateFormat formatter;

    class MyBinder extends Binder {
        public LocationService getLocationService() {
            return LocationService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return new MyBinder();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                mLocation = location;
            }

            @Override
            public void onProviderDisabled(@NonNull String provider) {
                mLocation = null;
            }

            @Override
            public void onProviderEnabled(@NonNull String provider) {
                mLocation = null;
            }
        };
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, mLocationListener);

        mSatelliteCount = 0;
        mGnssStatusCallback = new GnssStatus.Callback() {
            @Override
            public void onStarted() {
                super.onStarted();
                Log.d(TAG, "GnssStatus, onStarted");
            }

            @Override
            public void onFirstFix(int ttffMillis) {
                super.onFirstFix(ttffMillis);
                Log.d(TAG, "GnssStatus, onFirstFix");
            }

            @Override
            public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                super.onSatelliteStatusChanged(status);
                mSatelliteCount = status.getSatelliteCount();
                mBeidouSatelliteCount = 0;
                mGpsSatelliteCount = 0;
                for (int index = 0; index < mSatelliteCount; index++) {
                    switch (status.getConstellationType(index)) {
                        case GnssStatus.CONSTELLATION_BEIDOU:
                            mBeidouSatelliteCount++;
                            break;
                        case GnssStatus.CONSTELLATION_GPS:
                            mGpsSatelliteCount++;
                            break;
                        default:
                            break;
                    }
                }
            }
        };
        mLocationManager.registerGnssStatusCallback(mGnssStatusCallback);

        isConnecting = true;
        LocationThread mLocationThread = new LocationThread();
        new Thread(mLocationThread).start();
    }

    @SuppressLint("HandlerLeak")
    private class LocationThread implements Runnable {
        private int sleepDuration = 1000;
        private boolean checkGPS;
        private DecimalFormat dfLon;
        private DecimalFormat dfSpd;
        private SimpleDateFormat formatter;

        public LocationThread() {
            checkGPS = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            dfLon = new DecimalFormat("#.000000");  // (经纬度)保留小数点后六位
            dfSpd = new DecimalFormat("#0.00");  // (速度或航向)保留小数点后两位
            formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }

        public String printLocationMsg() {
            StringBuilder sb = new StringBuilder();
            sb.append("SysTime:\t");
//            sb.append(String.valueOf(System.currentTimeMillis()));
            sb.append(formatter.format(new Date(System.currentTimeMillis())));
            sb.append("\nTime:\t");
            sb.append(String.valueOf(mLocation.getTime()));
            sb.append("\nLongitude:\t");
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
            while (isConnecting) {
                if (callback != null) {
                    checkGPS = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                    if (!checkGPS) {
                        callback.onLocationProvoiderDisabled();
                        isConnecting = false;
                        break;
                    }
                    if (mLocation != null) {
                        callback.onLocationChange(mLocation);
                    } else {
                        callback.onLocationSearching("GNSS Searching ...\n" +
                                mBeidouSatelliteCount + " Beidou Satellites\n" +
                                mGpsSatelliteCount + " GPS Satellites");
                    }
                }
                try {
                    Thread.sleep(sleepDuration);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Log.d(TAG, "LocationThread End");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public Callback getCallback() {
        return callback;
    }

    public interface Callback {
//        void onLocationChange(String data);
        void onLocationChange(Location location);

        void onLocationProvoiderDisabled();

        void onLocationSearching(String data);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestory");

        isConnecting = false;
        if (mLocationManager != null) {
            mLocationManager.removeUpdates(mLocationListener);
            mLocationManager.unregisterGnssStatusCallback(mGnssStatusCallback);
            mLocationManager = null;
        }
    }
}