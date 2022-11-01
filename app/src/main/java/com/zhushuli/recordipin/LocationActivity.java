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
import com.zhushuli.recordipin.utils.FileUtils;
import com.zhushuli.recordipin.utils.LocationStrUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;


public class LocationActivity extends AppCompatActivity implements ServiceConnection, View.OnClickListener {

    private static final String TAG = "My" + LocationActivity.class.getSimpleName();
//    private static final String TAG = "My" + LocationActivity.class.getName();

    private static final int GNSS_LOCATION_UPDATE_CODE = 8402;
    private static final int GNSS_SEARCHING_CODE = 502;
    private static final int GNSS_PROVIDER_DISABLED_CODE = 404;
    private static final int GNSS_FILE_WRITING_CODE = 1010;
    private static final int GNSS_FILE_CLOSE_CODE = 2020;
    private static final int GNSS_DOWNLOAD_THREAD_INTERRUPT_CODE = 3030;

    private TextView tvLocationMsg;
    private Button btnLocServiceStart;

    private LocationService.MyBinder binder = null;

    private SimpleDateFormat formatter;

    private DownloadThread mDownloadThread;
    private String mRecordingDir;
    private ArrayList<String> mLocationStrList = new ArrayList<String>();

    private Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case GNSS_LOCATION_UPDATE_CODE:
                    String locationMsg = (String) msg.obj;
                    tvLocationMsg.setText(locationMsg);
                    break;
                case GNSS_SEARCHING_CODE:
                    String satelliteMsg = (String) msg.obj;
                    tvLocationMsg.setText(satelliteMsg);
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
        btnLocServiceStart.setOnClickListener(this);

        formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

        // 存储文件路径
        mRecordingDir = getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();

        mDownloadThread = new DownloadThread(mRecordingDir);
        new Thread(mDownloadThread).start();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(TAG, "onServiceConnected");
        binder = (LocationService.MyBinder) service;
        LocationService mLocationService = binder.getLocationService();
        mLocationService.setCallback(new LocationService.Callback() {
            @Override
            public void onLocationChanged(Location location) {
                Log.d(TAG, "onLocationChanged");
                Message msg = new Message();
                msg.what = GNSS_LOCATION_UPDATE_CODE;
                msg.obj = LocationStrUtils.printLocationMsg(location);
                mMainHandler.sendMessage(msg);

                // TODO::手机锁屏后无法保存数据
                writeLocation2File(location);
            }

            @Override
            public void onLocationProvoiderDisabled() {
                Message msg = Message.obtain();
                msg.what = GNSS_PROVIDER_DISABLED_CODE;
                mMainHandler.sendMessage(msg);

                unbindService(LocationActivity.this);
                binder = null;

                closeLocation2File();
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
                    binder = null;
                    tvLocationMsg.setText("Location Stop");

                    closeLocation2File();

                    btnLocServiceStart.setText("Start");
                }
                break;
            default:
                break;
        }
    }

    public void writeLocation2File(Location location) {
        // 每11秒保存一次
        if (mLocationStrList.size() > 10) {
            String csvString = "";
            for (String locStr : mLocationStrList) {
                csvString += locStr;
            }
            Message msg = Message.obtain();
            msg.what = GNSS_FILE_WRITING_CODE;
            msg.obj = csvString;
            mDownloadThread.getHandler().sendMessage(msg);
            mLocationStrList.clear();
            mLocationStrList.add(LocationStrUtils.genLocationCsv(location));
            Log.d(TAG, "writing");
        } else {
            mLocationStrList.add(LocationStrUtils.genLocationCsv(location));
        }
    }

    public void closeLocation2File() {
        Message msg = Message.obtain();
        msg.what = GNSS_FILE_CLOSE_CODE;
        if (mLocationStrList.size() > 0) {
            // TODO::Java拼接字符串列表
            String csvString = "";
            for (String locStr : mLocationStrList) {
                csvString += locStr;
            }
            msg.obj = csvString;
            mLocationStrList.clear();
        } else {
            msg.obj = null;
        }
        mDownloadThread.getHandler().sendMessage(msg);
    }

    public class DownloadThread implements Runnable {
        private BufferedWriter mBufferWriter;
        private Handler mHandler;
        private String mDirRootPath;
        private AtomicBoolean bFileWriterOpen = new AtomicBoolean(false);

        public DownloadThread(String dirRootPath) {
            mDirRootPath = dirRootPath;
        }

        private void initWriter() {
            String dirPath = mDirRootPath + File.separator + formatter.format(new Date(System.currentTimeMillis()));
            mBufferWriter = FileUtils.initWriter(dirPath, "GNSS.csv");
            try {
                mBufferWriter.write("sysTime,gnssTime,longitude,latitude,accuracy,speed,speedAccuracy,bearing,bearingAccuracy\n");
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "1111");
            }
        }

        @Override
        public void run() {
            Looper.prepare();
            Log.d(TAG, "Download Thread Start");
            mHandler = new Handler(Looper.myLooper()) {
                @Override
                public void handleMessage(@NonNull Message msg) {
                    switch (msg.what) {
                        case GNSS_DOWNLOAD_THREAD_INTERRUPT_CODE:
                            Looper.myLooper().quit();
                            break;
                        case GNSS_FILE_WRITING_CODE:
                            Log.d(TAG, "Download Thread Write");
                            if (!bFileWriterOpen.getAndSet(true)) {
                                initWriter();
                            }
                            try {
                                mBufferWriter.write((String) msg.obj);
                                mBufferWriter.flush();  // 缓冲区未满时强制数据写入
                            } catch (IOException e) {
                                e.printStackTrace();
                                Log.d(TAG, "2222");
                            }
                            break;
                        case GNSS_FILE_CLOSE_CODE:
                            Log.d(TAG, "Download Thread Close");
                            if (!bFileWriterOpen.get()) {
                                if (msg.obj != null) {
                                    initWriter();
                                } else {
                                    break;
                                }
                            }
                            try {
                                if (msg.obj != null) {
                                    mBufferWriter.write((String) msg.obj);
                                }
                                mBufferWriter.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            bFileWriterOpen.set(false);
                            break;
                        default:
                            break;
                    }
                }
            };
            Looper.loop();
            Log.d(TAG, "Download Thread Interrupt");
        }

        public Handler getHandler() {
            return mHandler;
        }
    }

    public Handler getMainHandler() {
        return mMainHandler;
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

    protected void interruptDownloadThread() {
        Message msg = Message.obtain();
        msg.what = GNSS_DOWNLOAD_THREAD_INTERRUPT_CODE;
        mDownloadThread.getHandler().sendMessage(msg);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestory");

        if (binder != null) {
            unbindService(LocationActivity.this);
            binder = null;
        }

        closeLocation2File();
        interruptDownloadThread();
    }
}