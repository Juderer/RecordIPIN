package com.zhushuli.recordipin.activities.wifi;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zhushuli.recordipin.BaseActivity;
import com.zhushuli.recordipin.R;
import com.zhushuli.recordipin.services.WiFiService;
import com.zhushuli.recordipin.utils.WiFiUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>Present WiFi scan results received by {@link WiFiService}.</p>
 * <br>
 * <p>WiFiActivity.java</p>
 * <br>
 * <p>Created date: 2024/06/18 18:41</p>
 * @author  : <a href="https://juderer.github.io">zhushuli</a>
 */
public class WiFiActivity extends BaseActivity {

    private static final String TAG = WiFiActivity.class.getSimpleName();

    private RecyclerView rvWiFiScanResults;

    private TextView tvAmount;

    private ImageButton btnScan;

    private List<ScanResult> mScanResults;

    private final AtomicBoolean scanning = new AtomicBoolean(false);

    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");

    private String mRecordRootDir;

    private String mRecordAbsDir;

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
                        if (mScanResults == null) {
                            mScanResults = scanResults;
                            rvWiFiScanResults.setAdapter(mWiFiAdapter);
                        } else {
                            mScanResults = scanResults;
                            mWiFiAdapter.notifyDataSetChanged();
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private WiFiService.WiFiBinder mWiFiBinder = null;

    private WiFiService mWiFiService;

    private final ServiceConnection mWiFiServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            mWiFiBinder = (WiFiService.WiFiBinder) service;
            mWiFiService = mWiFiBinder.getWiFiService();

            mRecordAbsDir = mRecordRootDir + File.separator + formatter.format(new Date(System.currentTimeMillis()));
            mWiFiService.startWiFiRecord(mRecordAbsDir);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
        }
    };

    private final BroadcastReceiver mWiFiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive");
            String action = intent.getAction();
            Log.d(TAG, action);

            if (action.equals(WiFiService.WIFI_SCAN_CHANGED_ACTION)) {
                List<ScanResult> scanResults = intent.getParcelableArrayListExtra("ScanResults");
                Log.d(TAG, "The amount of WiFi access points is " + scanResults.size());
                Message msg = Message.obtain();
                msg.what = WiFiService.WIFI_SCAN_CHANGED_CODE;
                msg.obj = scanResults;
                mMainHandler.sendMessage(msg);
            }
        }
    };

    private final HandlerThread mWiFiReceiveThread = new HandlerThread("WiFi Receiver");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set the theme of this activity
//        setTheme(R.style.Theme_RecordIPIN_NoActionBar2);
        setContentView(R.layout.activity_wifi);
        Log.d(TAG, "onCreate");

        rvWiFiScanResults = (RecyclerView) findViewById(R.id.rvWiFiScanResults);
        LinearLayoutManager layoutManager = new LinearLayoutManager(WiFiActivity.this);
        rvWiFiScanResults.setLayoutManager(layoutManager);
        rvWiFiScanResults.setItemAnimator(null);
        rvWiFiScanResults.addItemDecoration(new DividerItemDecoration(WiFiActivity.this,
                DividerItemDecoration.VERTICAL));

        btnScan = (ImageButton) findViewById(R.id.btnWiFiScan);
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!scanning.get()) {
                    scanning.set(true);
                    btnScan.setImageResource(R.drawable.baseline_pause_24);
                    bindService();
                } else {
                    scanning.set(false);
                    btnScan.setImageResource(R.drawable.baseline_play_arrow_24);
                    unbindService();
                }
            }
        });

        tvAmount = (TextView) findViewById(R.id.tvWiFiAmount);

        mRecordRootDir = getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();

        mWiFiAdapter = new WiFiAdapter();

        mWiFiReceiveThread.start();
    }

    private void bindService() {
        Intent wifiIntent = new Intent(WiFiActivity.this, WiFiService.class);
        bindService(wifiIntent, mWiFiServiceConn, BIND_AUTO_CREATE);
    }

    private void unbindService() {
        unbindService(mWiFiServiceConn);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        final IntentFilter wifiIntent = new IntentFilter();
        wifiIntent.addAction(WiFiService.WIFI_SCAN_CHANGED_ACTION);
        registerReceiver(mWiFiReceiver,
                wifiIntent,
                null,
                new Handler(mWiFiReceiveThread.getLooper()));
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");

        unregisterReceiver(mWiFiReceiver);

        if (rvWiFiScanResults.getChildCount() > 0 && scanning.get()) {
            rvWiFiScanResults.removeAllViews();
            mWiFiAdapter.notifyDataSetChanged();
            mScanResults = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        if (scanning.get()) {
            unbindService();
        }

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

    private class WiFiAdapter extends RecyclerView.Adapter<WiFiViewHolder> {

        private WifiManager mWiFiManager;

        @NonNull
        @Override
        public WiFiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            this.mWiFiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
//            View view = View.inflate(WiFiActivity.this, R.layout.wifi_list_item, null);
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.wifi_list_item, parent, false);
            return new WiFiViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull WiFiViewHolder holder, int position) {
            ScanResult scanResult = mScanResults.get(position);

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
            return mScanResults != null ? mScanResults.size() : 0;
        }
    }
}