package com.zhushuli.recordipin.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.zhushuli.recordipin.R;
import com.zhushuli.recordipin.utils.FileUtils;
import com.zhushuli.recordipin.utils.LocationUtils;
import com.zhushuli.recordipin.utils.ThreadUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
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

    // 子线程：位置监听与卫星状态监听信息整合
    private LocationThread mLocationThread;

    // 子线程：记录位置信息
    private RecordThread mRecordThread;
    private AtomicBoolean abRunning = new AtomicBoolean(false);

    // 定位时间间隔（毫秒）
    private static final int MIN_LOCATION_DURATION = 1000;

    private Queue<String> mStringQueue = new LinkedList<>();
    private Queue<Location> mLocationQueue = new LinkedList<>();

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


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        requestLocationUpdates();
        registerGnssStatus();

        mLocationThread = new LocationThread();
        new Thread(mLocationThread).start();

        // 启动前台服务, 确保APP长时间后台运行后返回前台无法更新定位
        Notification notification = createNotification();
        startForeground(1, notification);
    }

    private Notification createNotification() {
        String channelID = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelID = createNotificationChannel("com.zhushuli.recordipin", "foregroundservice");
        } else {
            channelID = "";
        }
        // TODO::notification的详细设计
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("RecordIPIN")
                .setContentText("正在使用定位服务")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        Notification notification = builder.build();
        return notification;
    }

    private String createNotificationChannel(String channelID, String channelName) {
        NotificationChannel channel = new NotificationChannel(channelID, channelName, NotificationManager.IMPORTANCE_HIGH);
        channel.setLightColor(Color.BLUE);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
        return channelID;
    }

    @SuppressLint("MissingPermission")
    private void requestLocationUpdates() {
        mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                Log.d(TAG, "onLocationChanged");
                mLocation = location;
                mLocationQueue.offer(location);
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
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_LOCATION_DURATION, 0, mLocationListener);
    }

    @SuppressLint("MissingPermission")
    private void registerGnssStatus() {
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
                // 耗时操作；尤其在室内无法定位时，会严重影响主线程
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
                Log.d(TAG, "BD" + mBeidouSatelliteCount + "GPS" + mGpsSatelliteCount);
            }
        };
        mLocationManager.registerGnssStatusCallback(mGnssStatusCallback);
    }

    @SuppressLint("HandlerLeak")
    private class LocationThread implements Runnable {
        private boolean checkGPS;
        private AtomicBoolean abConnected = new AtomicBoolean(true);

        public LocationThread() {
            checkGPS = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
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
                        callback.onLocationChanged(mLocation);
                        mLocation = null;
                    } else {
                        callback.onLocationSearching(mBeidouSatelliteCount + " " + "Beidou" +
                                "," + mGpsSatelliteCount + " " + "GPS");
                    }
                    ThreadUtils.threadSleep(MIN_LOCATION_DURATION);
                    Log.d(TAG, "LocationThread Show");
                }
            }
            Log.d(TAG, "LocationThread End");
        }
    }

    public void startLocationRecording(String mRecordingDir) {
        abRunning.set(true);
        mRecordThread = new RecordThread(mRecordingDir);
        new Thread(mRecordThread).start();
    }

    private class RecordThread implements Runnable {
        private String mRecordingDir;
        private BufferedWriter mBufferWriter;

        public RecordThread(String recordingDir) {
            mRecordingDir = recordingDir;
        }

        private void initWriter() {
            mBufferWriter = FileUtils.initWriter(mRecordingDir, "GNSS.csv");
            try {
                mBufferWriter.write("sysTime,elapsedTime,gnssTime,longitude,latitude,accuracy," +
                        "speed,speedAccuracy,bearing,bearingAccuracy\n");
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "1111");
            }
        }
        @Override
        public void run() {
            initWriter();
            int writeRowCount = 0;
            Log.d(TAG, "GNSS Record Start");
            while (LocationService.this.getAbRunning()) {
                if (mLocationQueue.size() > 0) {
                    try {
                        mBufferWriter.write(LocationUtils.genLocationCsv(mLocationQueue.poll()));
                        writeRowCount ++;
                        if (writeRowCount > 10) {
                            mBufferWriter.flush();
                            writeRowCount = 0;
                            Log.d(TAG, "GNSS Record Write");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            FileUtils.closeBufferedWriter(mBufferWriter);
            Log.d(TAG, "GNSS Record End");
        }
    }

    public boolean getAbRunning() {
        return abRunning.get();
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

        stopForeground(true);
        abRunning.set(false);
    }
}