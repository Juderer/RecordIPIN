package com.zhushuli.recordipin.services;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.telephony.CellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.preference.PreferenceManager;

import com.zhushuli.recordipin.utils.CellularUtils;
import com.zhushuli.recordipin.utils.FileUtils;
import com.zhushuli.recordipin.utils.ThreadUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author      : zhushuli
 * @createDate  : 2023/03/20 21:07
 * @description : 蜂窝网络服务2，提高蜂窝网络刷新频率
 */
public class CellularService2 extends Service {
    private static final String TAG = CellularService2.class.getSimpleName();

    public static final String CELLULAR_CELL_INFO_CHANGED_ACTION = "recordipin.broadcast.cellular.cellInfoChanged";

    public static final int CELLULAR_CELL_INFO_CHANGED_CODE = 0x3001;

    private SharedPreferences mSharedPreferences;

    // 信号扫描时间间隔（单位：毫秒）
    private int SCAN_INTERVAL = 3000;

    public class CellularBinder extends Binder {
        public CellularService2 getCellularService2() {
            return CellularService2.this;
        }
    }

    public interface Callback {
        void onCellInfoChanged(List<CellInfo> cellInfo);
    }

    private Callback callback = null;

    public Callback getCallback() {
        return this.callback;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(4);

    private TelephonyManager.CellInfoCallback mCellInfoCallback;

    @Deprecated
    private PhoneStateListener mPhoneStateListener;

    // 这部分代码暂未使用
    @RequiresApi(api = Build.VERSION_CODES.S)
    private class MyTelephonyCallback extends TelephonyCallback implements TelephonyCallback.CellInfoListener {

        @Override
        public void onCellInfoChanged(@NonNull List<CellInfo> cellInfo) {
            Log.d(TAG, "onCellInfoChanged");
            for (CellInfo cell : cellInfo) {
                Log.d(TAG, cell.toString());
            }
            if (callback != null) {
                callback.onCellInfoChanged(cellInfo);
            }
        }
    }

    private MyTelephonyCallback mTelephonyCallback;

    // 用于辅助扫描线程
    private AtomicBoolean scanning = new AtomicBoolean(false);

    // 基站数据扫描子线程
    private final ScanThread mScanThread = new ScanThread();

    // 是否将扫描到的基站数据写入文件
    private AtomicBoolean recording = new AtomicBoolean(false);

    // LTE/NR数据写入文件
    private static final int LTE_NR_RECORD_CODE = 0x0001;

    // 关闭LTE/NR数据写入
    private static final int LTE_NR_RECORD_CLOSE_CODE = 0x0010;

    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");

    // 写入文件的绝对路径
    private String mRecordAbsDir;

    // 基站数据写入子线程
    private RecordThread mRecordThread = null;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return new CellularBinder();
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SCAN_INTERVAL = Integer.valueOf(mSharedPreferences.getString("prefCellularFreq", "1")) * 1_000;
        Log.d(TAG, String.valueOf(SCAN_INTERVAL));

        recording.set(mSharedPreferences.getBoolean("prefCellularCollected", false));
        Log.d(TAG, String.valueOf(recording.get()));

        initScanResources();
        scanning.set(true);
        // 启动扫描子线程
        new Thread(mScanThread).start();
    }

    private void initScanResources() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mCellInfoCallback = new TelephonyManager.CellInfoCallback() {
                @Override
                public void onCellInfo(@NonNull List<CellInfo> cellInfo) {
                    Log.d(TAG, "onCellInfo:" + ThreadUtils.threadID());
                    for (CellInfo cell : cellInfo) {
                        Log.d(TAG, cell.toString());
                    }
                    // 基站信息写入文件
                    if (cellInfo.size() > 0 && recording.get() && mRecordThread != null) {
                        Message msg = Message.obtain();
                        msg.what = LTE_NR_RECORD_CODE;
                        msg.obj = cellInfo;
                        mRecordThread.getHandler().sendMessage(msg);
                    }
                    // 使用广播的方式实现与Activity通信
                    sendBroadcast(new Intent(CELLULAR_CELL_INFO_CHANGED_ACTION)
                            .putParcelableArrayListExtra("CellInfo", (ArrayList<? extends Parcelable>) cellInfo));
                    // 或使用回调接口实现与Activity通信
//                    while (callback == null) {
//                        ThreadUtils.sleep(10);
//                    }
//                    if (callback != null) {
//                        callback.onCellInfoChanged(cellInfo);
//                    }
                }
            };
        }
    }

    @SuppressLint("MissingPermission")
    private class ScanThread implements Runnable {
        @Override
        public void run() {
            while (scanning.get()) {
                final TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                Log.d(TAG, "ScanThread:" + System.identityHashCode(telephonyManager));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    telephonyManager.requestCellInfoUpdate(mExecutorService, mCellInfoCallback);
                }
                ThreadUtils.sleep(SCAN_INTERVAL);
            }
        }
    }

    public synchronized void startCellularRecord(String recordDir) {
        // 启动数据写入子线程
        if (recording.get() && mRecordThread == null) {
            mRecordAbsDir = recordDir;
            mRecordThread = new RecordThread(mRecordAbsDir);
            new Thread(mRecordThread).start();
        } else {
            Log.d(TAG, "Cellular record thread has been already RUNNING or cellular record is NOT allowed.");
        }
    }

    private class RecordThread implements Runnable {

        private Handler mRecordHandler;

        private BufferedWriter mBufferedWriter;

        private String mRecordDir;

        public RecordThread(String recordDir) {
            mRecordDir = recordDir;
        }

        public Handler getHandler() {
            return mRecordHandler;
        }

        private void initWriter() {
            mBufferedWriter = FileUtils.initWriter(mRecordDir, "Cellular.csv");
            try {
                mBufferedWriter.write("cellType,isRegistered,sysTime,elapsedTime,mcc,mnc,cid,tac,earfcn(nrarfcn),pci,rsrp,rsrq\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run() {
            Log.d(TAG, "LTE/NR Record Start:" + ThreadUtils.threadID());
            initWriter();
            Looper.prepare();
            mRecordHandler = new Handler(Looper.myLooper()) {
                @Override
                public void handleMessage(@NonNull Message msg) {
                    switch (msg.what) {
                        case LTE_NR_RECORD_CODE:
                            List<CellInfo> cellInfo = (List<CellInfo>) msg.obj;
                            try {
                                mBufferedWriter.write(CellularUtils.transCellInfo2Str(cellInfo));
                                mBufferedWriter.flush();
                                Log.d(TAG, "LTE/NR Record Write");
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            break;
                        case LTE_NR_RECORD_CLOSE_CODE:
                            FileUtils.closeBufferedWriter(mBufferedWriter);
                            Looper.myLooper().quitSafely();
                            break;
                        default:
                            break;
                    }
                }
            };
            Looper.loop();
            Log.d(TAG, "LTE/NR Record End");
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        if (recording.get() && mRecordThread != null) {
            mRecordThread.getHandler().sendEmptyMessage(LTE_NR_RECORD_CLOSE_CODE);
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        scanning.set(false);
        mExecutorService.shutdown();
    }
}