package com.zhushuli.recordipin.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.GnssClock;
import android.location.GnssMeasurementRequest;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.zhushuli.recordipin.R;
import com.zhushuli.recordipin.models.location.SatelliteInfo;
import com.zhushuli.recordipin.utils.FileUtils;
import com.zhushuli.recordipin.utils.location.LocationUtils;
import com.zhushuli.recordipin.utils.ThreadUtils;
import com.zhushuli.recordipin.utils.location.GnssClockUtils;
import com.zhushuli.recordipin.utils.location.SatelliteUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author      : zhushuli
 * @createDate  : 2023/02/26 10:30
 * @description : 位置服务2，将callback逻辑修改为broadcast；加入GnssClock
 */
public class LocationService2 extends Service {

    private static final String TAG = LocationService2.class.getSimpleName();

    private SharedPreferences mSharedPreferences;

    // GNSS位置更新码
    public static final int GNSS_LOCATION_CHANGED_CODE = 0x1001;
    // GNSS卫星状态码
    public static final int GNSS_SATELLITE_STATUS_CHANGED_CODE = 0x1002;
    // GNSS位置信息不可用码
    public static final int GNSS_PROVIDER_DISABLED_CODE = 0x1003;

    // GNSS位置更新广播
    public static final String GNSS_LOCATION_CHANGED_ACTION = "recordipin.broadcast.gnss.locationChanged";
    // GNSS卫星状态广播
    public static final String GNSS_PROVIDER_DISABLED_ACTION = "recordipin.broadcast.gnss.providerDisabled";
    // GNSS位置信息不可用广播
    public static final String GNSS_SATELLITE_STATUS_CHANGED_ACTION = "recordipin.broadcast.gnss.satelliteStatusChanged";

    // 定位时间间隔（毫秒）
    private static final int MIN_LOCATION_DURATION = 1000;

    // 位置监听相关类
    private LocationManager mLocationManager;

    private final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            Log.d(TAG, "onLocationChanged:" + ThreadUtils.threadID());
            if (checkRecording()) {
                mLocations.offer(LocationUtils.genLocationCsv(location));
            }
            sendBroadcast(new Intent(GNSS_LOCATION_CHANGED_ACTION).putExtra("Location", location));
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            Log.d(TAG, "onProviderDisabled");
            sendBroadcast(new Intent(GNSS_PROVIDER_DISABLED_ACTION));
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
            Log.d(TAG, "onProviderEnabled");
        }
    };

    // 卫星状态监听
    private final GnssStatus.Callback mGnssStatusCallback = new GnssStatus.Callback() {
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
            Log.d(TAG, "onSatelliteStatusChanged:" + ThreadUtils.threadID());

            List<SatelliteInfo> satelliteInfos = SatelliteUtils.genSatelliteInfos(status);

            sendBroadcast(new Intent(GNSS_SATELLITE_STATUS_CHANGED_ACTION)
                    .putParcelableArrayListExtra("Satellites", (ArrayList<SatelliteInfo>) satelliteInfos));
        }
    };

    private final GnssMeasurementsEvent.Callback mGnssMeasureEventCallback = new GnssMeasurementsEvent.Callback() {
        @Override
        public void onGnssMeasurementsReceived(GnssMeasurementsEvent eventArgs) {
            Log.d(TAG, "onGnssMeasurementsReceived:" + ThreadUtils.threadID());

            GnssClock clock = eventArgs.getClock();
            if (checkRecording()) {
                mGnssClocks.add(GnssClockUtils.genGnssClockCsv(clock));
            }

            long utcTimeNanos = GnssClockUtils.calcUtcTimeNanos(clock);
            long utcTimeMillis = GnssClockUtils.calcUtcTimeMillis(clock);

            Log.d(TAG, "GPS UTC: " + utcTimeNanos + "\t" + "SYS UTC: " + System.currentTimeMillis() * 1_000_000);
            Log.d(TAG, "GPS UTC: " + utcTimeMillis + "\t" + "SYS UTC: " + System.currentTimeMillis());
        }
    };

    private GnssMeasurementRequest mGnssMeasurementRequest;

    // 线程池（适用API level30+），用于卫星状态、位置变化监听
    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(2);

    // 监听卫星状态变化线程
    private final HandlerThread mGnssStatusThread = new HandlerThread("GnssStatus");

    // 监听位置变化线程
    private final HandlerThread mLocationListenerThread = new HandlerThread("LocationListener");

    private final HandlerThread mGnssMeasurementsThread = new HandlerThread("GnssMeasurements");

    // 用于保存数据的队列
    private Queue<String> mLocations = new LinkedList<>();

    private Queue<String> mGnssClocks = new LinkedList<>();

    // 子线程：记录位置信息，并写入文件
    private RecordThread mRecordThread;

    private GnssClockRecordThread mGnssClockRecordThread;

    // 判断"写入文件"子线程是否继续
    private AtomicBoolean recording = new AtomicBoolean(false);

    public boolean checkRecording() {
        return recording.get();
    }

    public void setRecording(boolean recording) {
        this.recording.set(recording);
    }

    public interface Callback {
        void onLocationChanged(Location location);

        void onLocationProviderDisabled();

        void onLocationSearching(String data);
    }

    private Callback callback;

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public Callback getCallback() {
        return callback;
    }

    public class LocationBinder extends Binder {
        public LocationService2 getLocationService2() {
            return LocationService2.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return new LocationBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate:" + ThreadUtils.threadID());

        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        requestLocationUpdates();
        registerGnssStatus();
        registerGnssMeasurements();

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        setRecording(mSharedPreferences.getBoolean("prefGnssCollected", false));
        Log.d(TAG, String.valueOf(checkRecording()));

        // 启动前台服务, 确保APP长时间后台运行后返回前台无法更新定位
        Notification notification = createNotification();
        startForeground(0x1000, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        Log.d(TAG, "onRebind");
    }

    private Notification createNotification() {
        String channelID;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelID = createNotificationChannel("com.zhushuli.recordipin", "foregroundservice");
        } else {
            channelID = "";
        }
        // TODO::notification的详细设计（保证用户可移除）
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("RecordIPIN")
                .setContentText("正在使用定位服务")
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        Notification notification = builder.build();
        return notification;
    }

    private String createNotificationChannel(String channelID, String channelName) {
        NotificationChannel channel = new NotificationChannel(channelID, channelName, NotificationManager.IMPORTANCE_HIGH);
        channel.setLightColor(Color.BLUE);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
        return channelID;
    }

    @SuppressLint("MissingPermission")
    private void requestLocationUpdates() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0,
                    0,
                    mExecutorService,
                    mLocationListener);
        } else {
            mLocationListenerThread.start();
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0,
                    0,
                    mLocationListener,
                    mLocationListenerThread.getLooper());
        }
    }

    @SuppressLint("MissingPermission")
    private void registerGnssStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mLocationManager.registerGnssStatusCallback(mExecutorService, mGnssStatusCallback);
        } else {
            mGnssStatusThread.start();
            mLocationManager.registerGnssStatusCallback(mGnssStatusCallback, new Handler(mGnssStatusThread.getLooper()));
        }
    }

    @SuppressLint("MissingPermission")
    private void registerGnssMeasurements() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mGnssMeasurementRequest = new GnssMeasurementRequest.Builder()
                    .setFullTracking(true)
                    .build();
            mLocationManager.registerGnssMeasurementsCallback(mGnssMeasurementRequest, mExecutorService, mGnssMeasureEventCallback);
        } else {
            mGnssMeasurementsThread.start();
            mLocationManager.registerGnssMeasurementsCallback(mGnssMeasureEventCallback, new Handler(mGnssMeasurementsThread.getLooper()));
        }
    }

    public synchronized void startLocationRecord(String recordDir) {
        if (checkRecording() && mRecordThread == null) {
            Log.d(TAG, "startLocationRecord");
            mRecordThread = new RecordThread(recordDir);
            new Thread(mRecordThread).start();
            mGnssClockRecordThread = new GnssClockRecordThread(recordDir);
            new Thread(mGnssClockRecordThread).start();
        } else {
            Log.d(TAG, "Location record thread has been already RUNNING or location record is NOT allowed.");
        }
    }

    private class RecordThread implements Runnable {

        private String mRecordDir;

        private BufferedWriter mBufferedWriter;

        public RecordThread(String recordDir) {
            mRecordDir = recordDir;
        }

        private void initWriter() {
            mBufferedWriter = FileUtils.initWriter(mRecordDir + File.separator + "GNSS", "GNSS.csv");
            try {
                mBufferedWriter.write("sysClockTimeNanos,sysTimeMillis,elapsedRealtimeNanos,elapsedRealtimeUncertaintyNanos" +
                        ",gnssTimeMillis,longitude,latitude,accuracy,speed,speedAccuracy,bearing,bearingAccuracy\n");
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "Record Thread Error");
            }
        }

        @Override
        public void run() {
            initWriter();
            int writeRowCount = 0;
            Log.d(TAG, "GNSS Record Start");
            while (LocationService2.this.checkRecording() || mLocations.size() > 0) {
                if (mLocations.size() > 0) {
                    try {
                        mBufferedWriter.write(mLocations.poll());
                        writeRowCount++;
                        if (writeRowCount > 10) {
                            mBufferedWriter.flush();
                            writeRowCount = 0;
                            Log.d(TAG, "GNSS Record Write");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            FileUtils.closeBufferedWriter(mBufferedWriter);
            Log.d(TAG, "GNSS Record End");
        }
    }

    private class GnssClockRecordThread implements Runnable {

        private String mRecordDir;

        private BufferedWriter mBufferedWriter;

        public GnssClockRecordThread(String recordDir) {
            mRecordDir = recordDir;
        }

        private void initWriter() {
            mBufferedWriter = FileUtils.initWriter(mRecordDir + File.separator + "GNSS", "GnssClock.csv");
            try {
                mBufferedWriter.write("sysClockTimeNanos,sysTimeMillis,elapsedRealtimeNanos,elapsedRealtimeUncertaintyNanos,utcTimeMillis\n");
                mBufferedWriter.flush();
            } catch (IOException e) {
                Log.d(TAG, "GnssClock Record Thread Error");
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run() {
            Log.d(TAG, "GnssClock Record Start");
            initWriter();
            int writeCount = 0;
            while (LocationService2.this.checkRecording() || mGnssClocks.size() > 0) {
                if (mGnssClocks.size() > 0) {
                    try {
                        mBufferedWriter.write(mGnssClocks.poll());
                        writeCount += 1;
                        if (writeCount > 10) {
                            mBufferedWriter.flush();
                            writeCount = 0;
                            Log.d(TAG, "GnssClock Record Write");
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            FileUtils.closeBufferedWriter(mBufferedWriter);
            Log.d(TAG, "GnssClock Record End");
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        mLocationManager.removeUpdates(mLocationListener);
        mLocationManager.unregisterGnssStatusCallback(mGnssStatusCallback);
        mLocationManager.unregisterGnssMeasurementsCallback(mGnssMeasureEventCallback);

        stopForeground(true);
        setRecording(false);

        ThreadUtils.interrupt(mGnssStatusThread);
        ThreadUtils.interrupt(mLocationListenerThread);
        ThreadUtils.interrupt(mGnssMeasurementsThread);

        mExecutorService.shutdown();
    }
}