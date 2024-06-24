package com.zhushuli.recordipin.activities.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zhushuli.recordipin.BaseActivity;
import com.zhushuli.recordipin.R;
import com.zhushuli.recordipin.services.WiFiService;
import com.zhushuli.recordipin.utils.ThreadUtils;
import com.zhushuli.recordipin.utils.WiFiUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author      : zhushuli
 * @createDate  : 2024/06/21 18:20
 * @description : Try to learn the scanning process of
 *                [android-network-survey](https://github.com/christianrowlands/android-network-survey)
 *                for avoiding WiFi scanning throttling.
 *                Here, we only show the scan results in a recycler view.
 */
public class WiFiActivity2 extends BaseActivity {

    private static final String TAG = WiFiActivity2.class.getSimpleName();

    private RecyclerView rvWiFiScanResults;

    private TextView tvAmount;

    private ImageButton btnScan;

    private SharedPreferences mSharedPreferences;

    private int WIFI_SCAN_INTERVAL = 30_000;

    private final AtomicBoolean scanning = new AtomicBoolean(false);

    private List<ScanResult> mWiFiScanResults;

    private WiFiAdapter mWiFiAdapter;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case WiFiService.WIFI_SCAN_CHANGED_CODE:
                    List<ScanResult> scanResults = (List<ScanResult>) msg.obj;
                    if (!scanResults.isEmpty()) {
                        tvAmount.setText(String.valueOf(scanResults.size()));
                        if (mWiFiScanResults == null) {
                            mWiFiScanResults = scanResults;
                            rvWiFiScanResults.setAdapter(mWiFiAdapter);
                        } else {
//                            mDiffResult = DiffUtil.calculateDiff(new WiFiActivity2.WiFiDiffCallback(mWiFiScanResults, scanResults), true);
//                            mDiffResult.dispatchUpdatesTo(mWiFiAdapter);
                            mWiFiScanResults = scanResults;
                            mWiFiAdapter.notifyDataSetChanged();
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private DiffUtil.DiffResult mDiffResult;

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
                Message msg = Message.obtain();
                msg.what = WiFiService.WIFI_SCAN_CHANGED_CODE;
                msg.obj = scanResults;
                mMainHandler.sendMessage(msg);
            }
        }
    };

    private final HandlerThread mWiFiReceiveThread = new HandlerThread("WiFi Receiver");

    private final Handler mWiFiReceiveHandler = new Handler();

    private WifiManager.ScanResultsCallback mScanResultsCallback = null;

    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(1);

    private final Runnable mScanner = new Runnable() {
        @Override
        public void run() {
            try {
                if (scanning.get()) {
                    boolean successful = mWiFiManager.startScan();
                }
            } catch (NullPointerException e) {
                // pass
            }
            mWiFiReceiveHandler.postDelayed(this, WIFI_SCAN_INTERVAL);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_RecordIPIN_NoActionBar2);
        setContentView(R.layout.activity_wifi);
        Log.d(TAG, "onCreate");

        rvWiFiScanResults = (RecyclerView) findViewById(R.id.rvWiFiScanResults);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(WiFiActivity2.this);
        rvWiFiScanResults.setLayoutManager(layoutManager);
        rvWiFiScanResults.setItemAnimator(null);
        rvWiFiScanResults.addItemDecoration(new DividerItemDecoration(WiFiActivity2.this,
                DividerItemDecoration.VERTICAL));

        tvAmount = (TextView) findViewById(R.id.tvWiFiAmount);

        btnScan = (ImageButton) findViewById(R.id.btnWiFiScan);
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!scanning.get()) {
                    scanning.set(true);
                    btnScan.setImageResource(R.drawable.baseline_pause_24);
                } else {
                    scanning.set(false);
                    btnScan.setImageResource(R.drawable.baseline_play_arrow_24);
                }
            }
        });

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        WIFI_SCAN_INTERVAL = Integer.valueOf(mSharedPreferences.getString("prefWiFiScanPeriod", "30")) * 1_000;
        Log.d(TAG, "WiFi scan interval = " + WIFI_SCAN_INTERVAL);

        mWiFiManager = (WifiManager) getBaseContext().getSystemService(Context.WIFI_SERVICE);
        if (mWiFiManager == null) {
            Log.e(TAG, "No WiFiManager.");
        }

        mWiFiAdapter = new WiFiAdapter();

        mWiFiReceiveThread.start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        boolean wifiEnabled = WiFiUtils.isWiFiEnabled(getApplicationContext());
        Log.d(TAG, "WiFi enabled = " + wifiEnabled);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mScanResultsCallback = new WifiManager.ScanResultsCallback() {
                @Override
                public void onScanResultsAvailable() {
                    Log.d(TAG, "onScanResultsAvailable");

                    List<ScanResult> scanResults = mWiFiManager.getScanResults();
                    Collections.sort(scanResults, new WiFiUtils.WiFiScanResultComparator());
                    Log.d(TAG, "The number of WiFi access points is " + scanResults.size());

                    boolean throttleEnabled = mWiFiManager.isScanThrottleEnabled();
                    Log.d(TAG, "isScanThrottleEnabled," + throttleEnabled);

                    Message msg = Message.obtain();
                    msg.what = WiFiService.WIFI_SCAN_CHANGED_CODE;
                    Collections.sort(scanResults, new WiFiScanResultComparator());
                    msg.obj = scanResults;
                    mMainHandler.sendMessage(msg);
                }
            };
            mWiFiManager.registerScanResultsCallback(mExecutorService, mScanResultsCallback);
        } else {
            final IntentFilter scanResultsIntentFilter = new IntentFilter();
            scanResultsIntentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            registerReceiver(mWiFiReceiver, scanResultsIntentFilter,
                    null, new Handler(mWiFiReceiveThread.getLooper()));
        }

        mWiFiReceiveHandler.postDelayed(mScanner, WIFI_SCAN_INTERVAL);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mWiFiManager.unregisterScanResultsCallback(mScanResultsCallback);
        } else {
            unregisterReceiver(mWiFiReceiver);
        }
        mWiFiReceiveHandler.removeCallbacks(mScanner);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        mWiFiReceiveThread.quitSafely();
    }

    private class WiFiViewHolder extends RecyclerView.ViewHolder {
        protected TextView tvSSID;

        protected TextView tvRSSI;

        protected TextView tvBSSID;

        protected TextView tvChannelWidth;

        public WiFiViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSSID = itemView.findViewById(R.id.tvSSID);
            tvRSSI = itemView.findViewById(R.id.tvRSSI);
            tvBSSID = itemView.findViewById(R.id.tvBSSID);
            tvChannelWidth = itemView.findViewById(R.id.tvChannelWidth);
        }
    }

    private class WiFiAdapter extends RecyclerView.Adapter<WiFiActivity2.WiFiViewHolder> {

        private WifiManager mWiFiManager;

        @NonNull
        @Override
        public WiFiActivity2.WiFiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            this.mWiFiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
//            View view = View.inflate(WiFiActivity.this, R.layout.wifi_list_item, null);
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.wifi_list_item, parent, false);
            return new WiFiActivity2.WiFiViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull WiFiActivity2.WiFiViewHolder holder, int position) {
            ScanResult scanResult = mWiFiScanResults.get(position);

            if (scanResult.SSID.equals("")) {
                holder.tvSSID.setText("\"Unknown\"");
            } else {
                holder.tvSSID.setText(scanResult.SSID);
            }
            holder.tvSSID.setTextColor(getColor(R.color.DodgerBlue));

            holder.tvBSSID.setText("BSSID: " + scanResult.BSSID);

            holder.tvChannelWidth.setText(String.format("ChannelWidth: %dMHZ",
                    WiFiUtils.getChannelWidth(scanResult.channelWidth)));

            holder.tvRSSI.setText(String.format("%ddBm", scanResult.level));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                int level = mWiFiManager.calculateSignalLevel(scanResult.level);
                if (level == mWiFiManager.getMaxSignalLevel()) {
                    holder.tvRSSI.setTextColor(getColor(R.color.TealGreen));
                } else if (level == Math.max(mWiFiManager.getMaxSignalLevel() - 1, 0)) {
                    holder.tvRSSI.setTextColor(getColor(R.color.Goldenrod));
                } else if (level == Math.max(mWiFiManager.getMaxSignalLevel() - 2, 0)) {
                    holder.tvRSSI.setTextColor(getColor(R.color.darkorange));
                } else {
                    holder.tvRSSI.setTextColor(getColor(R.color.Red));
                }
            } else {
                int level = mWiFiManager.calculateSignalLevel(scanResult.level, 5);
                if (level >= 4) {
                    holder.tvRSSI.setTextColor(getColor(R.color.TealGreen));
                } else if (level >= 3) {
                    holder.tvRSSI.setTextColor(getColor(R.color.Goldenrod));
                } else if (level >= 2) {
                    holder.tvRSSI.setTextColor(getColor(R.color.darkorange));
                } else {
                    holder.tvRSSI.setTextColor(getColor(R.color.Red));
                }
            }
        }

        @Override
        public int getItemCount() {
            return mWiFiScanResults != null ? mWiFiScanResults.size() : 0;
        }
    }

    private class WiFiDiffCallback extends DiffUtil.Callback {

        private List<ScanResult> olds;

        private List<ScanResult> news;

        public WiFiDiffCallback(List<ScanResult> olds, List<ScanResult> news) {
            this.olds = olds;
            this.news = news;
        }

        @Override
        public int getOldListSize() {
            return this.olds != null ? this.olds.size() : 0;
        }

        @Override
        public int getNewListSize() {
            return this.news != null ? this.news.size() : 0;
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return this.olds.get(oldItemPosition).BSSID == this.news.get(newItemPosition).BSSID;
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return this.olds.get(oldItemPosition).equals(news.get(newItemPosition));
        }
    }

    private class WiFiScanResultComparator implements Comparator<ScanResult> {
        @Override
        public int compare(ScanResult o1, ScanResult o2) {
            return o2.level - o1.level;
        }
    }
}