/*
 * Copyright 2023 SHULI ZHU

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zhushuli.recordipin.services;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcelable;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.zhushuli.recordipin.utils.FileUtils;
import com.zhushuli.recordipin.utils.ThreadUtils;
import com.zhushuli.recordipin.utils.WiFiUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author      : zhushuli
 * @createDate  : 2024/06/18 17:13
 * @description : WiFi service for scanning and recording.
 */
public class WiFiService extends Service {

    private static final String TAG = WiFiService.class.getSimpleName();

    public static final String WIFI_SCAN_CHANGED_ACTION = "recordipin.broadcast.wifi.scanResultsChanged";

    public static final int WIFI_SCAN_CHANGED_CODE = 0x4001;

    private SharedPreferences mSharedPreferences;

    private int WIFI_SCAN_INTERVAL = 30_000;  // 30 seconds

    public class WiFiBinder extends Binder {
        public WiFiService getWiFiService() {
            return WiFiService.this;
        }
    }

    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(2);

    private WifiManager mWiFiManager;

    private final BroadcastReceiver mWiFiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive: " + ThreadUtils.threadID());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Pass
            } else {
                boolean successful = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                Log.d(TAG, "onReceive: " + successful);
                final List<ScanResult> scanResults = mWiFiManager.getScanResults();
                for (ScanResult result : scanResults) {
                    Log.d(TAG, result.toString());
                    break;
                }

                if (scanResults.size() > 0 && recording.get() && mRecorder != null) {
                    try {
                        mSemaphore.acquire();
                        mScanResults.offer(scanResults);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    } finally {
                        mSemaphore.release();
                    }
                }

                sendBroadcast(new Intent(WIFI_SCAN_CHANGED_ACTION)
                        .putParcelableArrayListExtra("ScanResults", (ArrayList<? extends Parcelable>) scanResults));
            }
        }
    };

    private final HandlerThread mWiFiReceiveThread = new HandlerThread("WiFi Receiver");

    private WifiManager.ScanResultsCallback mScanResultsCallback = null;

    private AtomicBoolean scanning = new AtomicBoolean(false);

    private final Scanner mScanner = new Scanner();

    private AtomicBoolean recording = new AtomicBoolean(false);

    private final Semaphore mSemaphore = new Semaphore(1);

    private Queue<String> mScanResultStrs = new LinkedList<>();

    private Queue<List<ScanResult>> mScanResults = new LinkedList<>();

    private String mRecordAbsDir;

    private Recorder mRecorder;

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBinder");
        return new WiFiBinder();
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        WIFI_SCAN_INTERVAL = Integer.valueOf(mSharedPreferences.getString("prefWiFiScanPeriod", "5")) * 1_000;
        Log.d(TAG, String.valueOf(WIFI_SCAN_INTERVAL));

        recording.set(mSharedPreferences.getBoolean("prefWiFiCollected", false));
        Log.d(TAG, String.valueOf(recording.get()));

        mWiFiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (mWiFiManager == null) {
            Log.e(TAG, "No WiFiManager.");
        }

        initScanResources();
        scanning.set(true);
        new Thread(mScanner).start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    private void initScanResources() {
        boolean wifiEnabled = WiFiUtils.isWiFiEnabled(getApplicationContext());
        Log.d(TAG, "WiFi enabled = " + wifiEnabled);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mScanResultsCallback = new WifiManager.ScanResultsCallback() {
                @Override
                public void onScanResultsAvailable() {
                    Log.d(TAG, "onScanResultsAvailable," + ThreadUtils.threadID());

                    List<ScanResult> scanResults = mWiFiManager.getScanResults();
                    Collections.sort(scanResults, new WiFiUtils.WiFiScanResultComparator());
                    Log.d(TAG, "The number of WiFi access points is " + scanResults.size());

                    boolean throttleEnabled = mWiFiManager.isScanThrottleEnabled();
                    Log.d(TAG, "isScanThrottleEnabled," + throttleEnabled);

                    if (scanResults.size() > 0 && recording.get() && mRecorder != null) {
                        try {
                            mSemaphore.acquire();
                            mScanResults.offer(scanResults);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        } finally {
                            mSemaphore.release();
                        }
                    }

                    sendBroadcast(new Intent(WIFI_SCAN_CHANGED_ACTION)
                            .putParcelableArrayListExtra("ScanResults", (ArrayList<? extends Parcelable>) scanResults));
                }
            };

            mWiFiManager.registerScanResultsCallback(mExecutorService, mScanResultsCallback);
        } else {
            mWiFiReceiveThread.start();

            final IntentFilter scanResultsIntentFilter = new IntentFilter();
            scanResultsIntentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            registerReceiver(mWiFiReceiver, scanResultsIntentFilter,
                    null, new Handler(mWiFiReceiveThread.getLooper()));
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        if (recording.get() && mRecorder != null) {
            recording.set(false);
            mRecorder = null;
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mWiFiManager.unregisterScanResultsCallback(mScanResultsCallback);
        } else {
            unregisterReceiver(mWiFiReceiver);
            mWiFiReceiveThread.quitSafely();
        }

        scanning.set(false);
        mExecutorService.shutdown();
    }

    private class Scanner implements Runnable {
        @Override
        public void run() {
            while (scanning.get()) {
                try {
                    boolean successful = mWiFiManager.startScan();
                } catch (NullPointerException e) {
                    // Pass
                }
                ThreadUtils.sleep(WIFI_SCAN_INTERVAL);
            }
        }
    }

    public synchronized void startWiFiRecord(String recordDir) {
        if (recording.get() && mRecorder == null) {
            mRecordAbsDir = recordDir;
            mRecorder = new Recorder(mRecordAbsDir);
            new Thread(mRecorder).start();
        } else {
            Log.d(TAG, "WiFi record thread has been already RUNNING or WiFi record is NOT allowed.");
        }
    }

    private class Recorder implements Runnable {

        private BufferedWriter mWriter;

        private String mRecordDir;

        public Recorder(String recordDir) {
            this.mRecordDir = recordDir;
        }

        private void initWriter() {
            this.mWriter = FileUtils.initWriter(mRecordDir, "WiFi.csv");
        }

        @Override
        public void run() {
            Log.d(TAG, "Recorder starts:" + ThreadUtils.threadID());
            initWriter();
            while (recording.get() || mScanResults.size() > 0) {
                if (mScanResults.size() > 0) {
                    try {
                        mSemaphore.acquire();
                        this.mWriter.write(WiFiUtils.transScanResults2Str(mScanResults.poll()));
                        this.mWriter.flush();
                        Log.d(TAG, "Recorder writes,");
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } finally {
                        mSemaphore.release();
                    }
                }
            }
            FileUtils.closeBufferedWriter(this.mWriter);
            Log.d(TAG, "Recorder finishes.");
        }
    }
}