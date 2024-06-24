package com.zhushuli.recordipin.services;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellSignalStrengthLte;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.zhushuli.recordipin.models.cellular.CellPacket;
import com.zhushuli.recordipin.models.cellular.CellNeighborLte;
import com.zhushuli.recordipin.models.cellular.CellNeighborNr;
import com.zhushuli.recordipin.models.cellular.CellServiceLte;
import com.zhushuli.recordipin.models.cellular.CellServiceNr;
import com.zhushuli.recordipin.utils.FileUtils;
import com.zhushuli.recordipin.utils.ThreadUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Deprecated
public class CellularService extends Service {

    private static final String TAG = CellularService.class.getSimpleName();

    public static final String CELLULAR_INFO_CHANGED_ACTION = "recordipin.broadcast.cellular.cellInfoChanged";

    private static final int RECORD_CELL_CODE = 1000;
    private static final int RECORD_CELL_LTE_CODE = 1001;
    private static final int RECORD_CELL_NR_CODE = 1002;
    private static final int RECORD_CELL_CLOSE_CODE = 2000;

    private TelephonyManager mTelephonyManager;
    private MyTelephonyCallback mTelephonyCallback;
    private MyPhoneStateListener mPhoneStateListener;
    private MyCellInfoCallback mCellInfoCallback;

    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(2);;

    // 蜂窝网络数据写入文件子线程
    private RecordThread mCellRecordThread;
    private AtomicBoolean abRecording = new AtomicBoolean(false);

    public CellularService() {
        Log.d(TAG, "CellularService");  // 先于onCreate调用！
    }

    public class MyBinder extends Binder {
        public CellularService getCellularService() {
            return CellularService.this;
        }
    }

    public interface Callback {
//        void onCellInfoChanged(List<CellInfo> cellInfo);
        void onCellInfoChanged(List<CellInfo> cellInfo);
        void onDataConnectionStateChanged(int state, int networkType);
    }

    private Callback callback = null;

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return new MyBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate" + Thread.currentThread().getId());

        // 注意！需要打开手机打开"位置信息"！
        // 重大发现？当手机启动了位置服务时（手机状态栏出现位置角标），基站的刷新频率会显著加快？
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.d(TAG, "TelephonyCallback");
            mTelephonyCallback = new MyTelephonyCallback();
            // 开启子线程
            mTelephonyManager.registerTelephonyCallback(mExecutorService, mTelephonyCallback);
//            mCellInfoCallback = new MyCellInfoCallback();
//            mTelephonyManager.requestCellInfoUpdate(mExecutorService, mCellInfoCallback);
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
            Log.d(TAG, "PhoneStateListener");

//            mCellInfoCallback = new MyCellInfoCallback();
//            mTelephonyManager.requestCellInfoUpdate(mExecutorService, mCellInfoCallback);

            // 需要加入Executor！否则在同一线程中，callback初始化会造成阻塞！
            mPhoneStateListener = new MyPhoneStateListener(mExecutorService);
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CELL_INFO);
        }
    }

    private static boolean checkCellInfo(CellInfo cell) {
        if (cell instanceof CellInfoLte) {
            CellInfoLte cellLte = (CellInfoLte) cell;
            CellSignalStrengthLte cssLte = cellLte.getCellSignalStrength();
            if (cssLte.getRsrp() == CellInfo.UNAVAILABLE || cssLte.getRsrq() == CellInfo.UNAVAILABLE) {
                return false;
            }
        }
        return true;
    }

    public static List<CellPacket> transDataFormat(List<CellInfo> cellInfo) {
        List<CellPacket> records = new ArrayList<>();

        for (CellInfo cell : cellInfo) {
            if (!checkCellInfo(cell)) {
                continue;
            }
            if (cell.isRegistered()) {
                if (cell instanceof CellInfoLte) {
                    records.add(new CellServiceLte(cell));
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (cell instanceof CellInfoNr) {
                        records.add(new CellServiceNr(cell));
                    }
                }
            }
            else {
                if (cell instanceof CellInfoLte) {
                    records.add(new CellNeighborLte(cell));
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (cell instanceof CellInfoNr) {
                        records.add(new CellNeighborNr(cell));
                    }
                }
            }
        }

        return records;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private class MyTelephonyCallback extends TelephonyCallback implements TelephonyCallback.CellInfoListener,
            TelephonyCallback.DataConnectionStateListener {
        @Override
        public void onCellInfoChanged(@NonNull List<CellInfo> cellInfo) {
            Log.d(TAG, "onCellInfoChanged" + cellInfo.size());
            Log.d(TAG, "onCellInfoChanged, threadID" + Thread.currentThread().getId());

//            List<CellPacket> records = transDataFormat(cellInfo);
//            for (CellPacket record : records) {
//                Log.d(TAG, record.toString() + ";" + System.currentTimeMillis());
//            }
//
//            ArrayList<String> strRecords = new ArrayList<>();
//            for (CellInfo cell : cellInfo) {
//                strRecords.add(JSON.toJSONString(cell));
//            }
//
//            // 写入文件
//            if (abRecording.get()) {
//                Message msg = Message.obtain();
//                msg.what = RECORD_CELL_CODE;
//                msg.obj = records;
//                mCellRecordThread.getHandler().sendMessage(msg);
//            }
//            sendBroadcast(new Intent(CELLULAR_INFO_CHANGED_ACTION).putStringArrayListExtra("CellInfo", strRecords));

            while (callback == null) {
                ThreadUtils.sleep(10);
            }
            if (callback != null) {
                // 页面显示
                callback.onCellInfoChanged(cellInfo);
                for (CellInfo cell : cellInfo) {
                    Log.d(TAG, cell.toString());
                }

                // 写入文件
                if (abRecording.get()) {
                    List<CellPacket> records = transDataFormat(cellInfo);
                    Message msg = Message.obtain();
                    msg.what = RECORD_CELL_CODE;
                    msg.obj = records;
                    mCellRecordThread.getHandler().sendMessage(msg);
                }
            }
        }

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            // 监听网络变化（如4G/5G切换）
            Log.d(TAG, "onDataConnectionStateChanged" + state + ";" + networkType);
            while (callback == null) {
                ThreadUtils.sleep(10);
            }
            if (callback != null) {
                callback.onDataConnectionStateChanged(state, networkType);
            }
        }
    }

    private class MyPhoneStateListener extends PhoneStateListener {

        @RequiresApi(api = Build.VERSION_CODES.Q)
        public MyPhoneStateListener(Executor executor) {
            super(executor);
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCellInfoChanged(List<CellInfo> cellInfo) {
            super.onCellInfoChanged(cellInfo);
            Log.d(TAG, "onCellInfoChanged" + cellInfo.size());
            Log.d(TAG, "onCellInfoChanged, threadID" + Thread.currentThread().getId());

            while (callback == null) {
                ThreadUtils.sleep(10);
            }
            if (callback != null) {
                // 页面显示
                callback.onCellInfoChanged(cellInfo);

                // 写入文件
                if (abRecording.get()) {
                    List<CellPacket> records = transDataFormat(cellInfo);
                    Message msg = Message.obtain();
                    msg.what = RECORD_CELL_CODE;
                    msg.obj = records;
                    mCellRecordThread.getHandler().sendMessage(msg);
                }
            }
        }

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            Log.d(TAG, "onDataConnectionStateChanged");
            if (callback != null) {
                callback.onDataConnectionStateChanged(state, networkType);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private class MyCellInfoCallback extends TelephonyManager.CellInfoCallback {

        @Override
        public void onCellInfo(@NonNull List<CellInfo> cellInfo) {
            Log.d(TAG, "onCellInfo" + cellInfo.size());

            callback.onCellInfoChanged(cellInfo);
            List<CellPacket> records = transDataFormat(cellInfo);
            for (CellPacket record : records) {
                Log.d(TAG, record.toString() + ";" + System.currentTimeMillis());
            }
        }
    }

    public void startCellularRecording(String recordingDir) {
        mCellRecordThread = new RecordThread(recordingDir);
        // TODO::Java/Android启动线程run与start的区别
        new Thread(mCellRecordThread).start();
        abRecording.set(true);
    }

    private class RecordThread implements Runnable {
        private Handler mHandler;
        private BufferedWriter mBufferedWriter;
        private String mRecordingDir;

        public RecordThread(String recordingDir) {
            mRecordingDir = recordingDir;
        }

        private void initWriter() {
            mBufferedWriter = FileUtils.initWriter(mRecordingDir, "Cellular.csv");
            try {
                mBufferedWriter.write("cellType,sysTime,elapsedTime,mcc,mnc,cid,tac,earfcn,pci,rsrp,rsrq\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            Log.d(TAG, "recordThread" + Thread.currentThread().getId());
            initWriter();
            Looper.prepare();
            mHandler = new Handler(Looper.myLooper()) {
                @Override
                public void handleMessage(@NonNull Message msg) {
                    switch (msg.what) {
                        case RECORD_CELL_CODE:
                            List<CellPacket> records = (List<CellPacket>) msg.obj;
                            long sysTime = System.currentTimeMillis();
                            StringBuffer sb = new StringBuffer();
                            for (CellPacket record : records) {
                                if (record instanceof CellServiceLte || record instanceof CellNeighborLte) {
                                    sb.append("lte");
                                    sb.append(",");
                                }
                                else if (record instanceof CellServiceNr || record instanceof CellNeighborNr) {
                                    sb.append("nr");
                                    sb.append(",");
                                }
                                sb.append(sysTime);
                                sb.append(",");
                                sb.append(record.toString());
                                sb.append("\n");
                            }
                            try {
                                mBufferedWriter.write(sb.toString());
                                mBufferedWriter.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        case RECORD_CELL_CLOSE_CODE:
                            FileUtils.closeBufferedWriter(mBufferedWriter);
                            break;
                        default:
                            break;
                    }
                }
            };
            Looper.loop();
            Log.d(TAG, "record thread end");
        }

        public Handler getHandler() {
            return mHandler;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        if (mTelephonyManager instanceof TelephonyManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mTelephonyManager.unregisterTelephonyCallback(mTelephonyCallback);
                Log.d(TAG, ">=S unregister");
            }
            else
            {
                mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
                mExecutorService.shutdown();
                Log.d(TAG, "<S unregister");
            }
        }

        if (abRecording.get()) {
            Message msg = new Message();
            msg.what = RECORD_CELL_CLOSE_CODE;
            mCellRecordThread.getHandler().handleMessage(msg);
        }
    }
}