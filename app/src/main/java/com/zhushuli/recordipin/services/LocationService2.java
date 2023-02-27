package com.zhushuli.recordipin.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
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

import com.alibaba.fastjson.JSON;
import com.zhushuli.recordipin.R;
import com.zhushuli.recordipin.utils.FileUtils;
import com.zhushuli.recordipin.utils.LocationUtils;
import com.zhushuli.recordipin.utils.ThreadUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author      : zhushuli
 * @createDate  : 2023/02/26 10:30
 * @description : 位置服务2，将callback逻辑修改为broadcast
 */
public class LocationService2 extends Service {

    private static final String TAG = LocationService2.class.getSimpleName();

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

    // 用于保存数据的队列
    private Queue<String> mStringQueue = new LinkedList<>();
    private Queue<Location> mLocationQueue = new LinkedList<>();

    // 位置监听相关类
    private LocationManager mLocationManager;

    private final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            Log.d(TAG, "onLocationChanged:" + ThreadUtils.threadID());
            if (checkRecording()) {
                mLocationQueue.offer(location);
            }
            sendBroadcast(new Intent(GNSS_LOCATION_CHANGED_ACTION)
                    .putExtra("location", JSON.toJSONString(LocationUtils.genLocationMap(location))));
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

    // 总卫星数
    private int mSatelliteCount;
    // 北斗卫星数
    private int mBeidouSatelliteCount;
    // GPS卫星数
    private int mGpsSatelliteCount;

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
            mSatelliteCount = status.getSatelliteCount();
            mBeidouSatelliteCount = 0;
            mGpsSatelliteCount = 0;
            for (int index = 0; index < mSatelliteCount; index++) {
                if (!status.usedInFix(index)) {
                    continue;
                }
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
            sendBroadcast(new Intent(GNSS_SATELLITE_STATUS_CHANGED_ACTION)
                    .putExtra("satellite", "BD" + mBeidouSatelliteCount + "," + "GPS" + mGpsSatelliteCount));
        }
    };

    // TODO::捕获GNSS原始测量值
    private final GnssMeasurementsEvent.Callback mGnssMeasureEventCallback = new GnssMeasurementsEvent.Callback() {
        @Override
        public void onGnssMeasurementsReceived(GnssMeasurementsEvent eventArgs) {
            super.onGnssMeasurementsReceived(eventArgs);
        }

    };

    // 线程池（适用API level30+），用于卫星状态、位置变化监听
    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(2);

    // 监听卫星状态变化线程
    private final HandlerThread mGnssStatusThread = new HandlerThread("GnssStatus");

    // 监听位置变化线程
    private final HandlerThread mLocationListenerThread = new HandlerThread("LocationListener");

    // 子线程：记录位置信息，并写入文件
    private RecordThread mRecordThread;
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

    public class MyBinder extends Binder {
        public LocationService2 getLocationService2() {
            return LocationService2.this;
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
        Log.d(TAG, "onCreate:" + ThreadUtils.threadID());

        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        requestLocationUpdates();
        registerGnssStatus();

        // 启动前台服务, 确保APP长时间后台运行后返回前台无法更新定位
        Notification notification = createNotification();
        startForeground(1, notification);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mExecutorService, mLocationListener);
        } else {
            mLocationListenerThread.start();
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener, mLocationListenerThread.getLooper());
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

    public synchronized void startLocationRecord(String recordDir) {
        if (!checkRecording()) {
            setRecording(true);
            mRecordThread = new RecordThread(recordDir);
            new Thread(mRecordThread).start();
        }
    }

    private class RecordThread implements Runnable {
        private String mRecordDir;
        private BufferedWriter mBufferWriter;

        public RecordThread(String recordDir) {
            mRecordDir = recordDir;
        }

        private void initWriter() {
            mBufferWriter = FileUtils.initWriter(mRecordDir, "GNSS.csv");
            try {
                mBufferWriter.write("sysTime,elapsedTime,gnssTime,longitude,latitude,accuracy," +
                        "speed,speedAccuracy,bearing,bearingAccuracy\n");
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
            while (LocationService2.this.checkRecording()) {
                if (mLocationQueue.size() > 0) {
                    try {
                        mBufferWriter.write(LocationUtils.genLocationCsv(mLocationQueue.poll()));
                        writeRowCount++;
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

        stopForeground(true);
        setRecording(false);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            mGnssStatusThread.quitSafely();
            mLocationListenerThread.quitSafely();
        }
    }
}