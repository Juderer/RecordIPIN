package com.zhushuli.recordipin.service;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicBoolean;

public class LocationService extends Service {

    private static final String TAG = "My" + LocationService.class.getSimpleName();

    private Callback callback;

    // 位置监听相关类
    private LocationManager mLocationManager;
    private LocationListener mLocationListener;
    private Location mLocation;

    // 卫星状态监听
    private GnssStatus.Callback mGnssStatusCallback;
    private int mSatelliteCount;
    private int mBeidouSatelliteCount;
    private int mGpsSatelliteCount;

    // 子线程
    private LocationThread mLocationThread;

    public class MyBinder extends Binder {
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
                Log.d(TAG, "onLocationChanged");
                mLocation = location;
            }

            @Override
            public void onProviderDisabled(@NonNull String provider) {
                Log.d(TAG, "onProviderDisabled");
                mLocation = null;
            }

            @Override
            public void onProviderEnabled(@NonNull String provider) {
                Log.d(TAG, "onProviderEnabled");
                mLocation = null;
            }
        };
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);

        mGnssStatusCallback = new GnssStatus.Callback() {
            @Override
            public void onStopped() {
                super.onStopped();
                Log.d(TAG, "GnssStatus, onStopped");
            }

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

        mLocationThread = new LocationThread();
        new Thread(mLocationThread).start();
    }

    @SuppressLint("HandlerLeak")
    private class LocationThread implements Runnable {
        private int sleepDuration = 1000;
        private boolean checkGPS;
        private Location mPreLoation;
        private AtomicBoolean abConnected = new AtomicBoolean(true);

        public LocationThread() {
            checkGPS = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            mPreLoation = null;
        }

        public boolean getConnected() {
            return abConnected.get();
        }

        public void setConnected(boolean connected) {
            abConnected.set(connected);
        }

        @Override
        public void run() {
            Log.d(TAG, "LocationThread Start");
            while (abConnected.get()) {
                if (callback != null) {
                    checkGPS = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                    if (!checkGPS) {
                        callback.onLocationProvoiderDisabled();
                        abConnected.set(false);
                        break;
                    }
                    if (mLocation != null) {
                        if (mPreLoation == null) {
                            mPreLoation = mLocation;
                        } else {
                            if (!mPreLoation.equals(mLocation)) {
                                callback.onLocationChanged(mLocation);
                            }
                            mPreLoation = mLocation;
                        }
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
        void onLocationChanged(Location location);

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

        if (mLocationThread.getConnected()) {
            mLocationThread.setConnected(false);
        }
        mLocationManager.removeUpdates(mLocationListener);
        mLocationManager.unregisterGnssStatusCallback(mGnssStatusCallback);
    }
}