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

package com.zhushuli.recordipin.activities.location;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.zhushuli.recordipin.BaseActivity;
import com.zhushuli.recordipin.R;
import com.zhushuli.recordipin.models.location.SatelliteInfo;
import com.zhushuli.recordipin.services.LocationService2;
import com.zhushuli.recordipin.utils.DialogUtils;
import com.zhushuli.recordipin.views.GnssSkyView;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author      : zhushuli
 * @createDate  : 2023/10/23 21:22
 * @description : GNSS Sky View.
 */
public class GnssSkyViewActivity extends BaseActivity {

    private static final String TAG = GnssSkyViewActivity.class.getSimpleName();

    private GnssSkyView mSkyView;

    private RecyclerView rvSatellite;

    private List<SatelliteInfo> mSatellites;

    private SatelliteAdapter mSatelliteAdapter;

    private DiffUtil.DiffResult mDiffResult;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case LocationService2.GNSS_SATELLITE_STATUS_CHANGED_CODE:
                    List<SatelliteInfo> satellites = (List<SatelliteInfo>) msg.obj;

                    if (satellites.size() > 0) {
                        if (mSatellites == null) {
                            mSatellites = satellites;
                            rvSatellite.setAdapter(mSatelliteAdapter);
                        }
                        else {
//                            mDiffResult = DiffUtil.calculateDiff(
//                                    new SatelliteDiffCallback(mSatellites, satellites), true);
//                            mDiffResult.dispatchUpdatesTo(mSatelliteAdapter);

                            mSatellites = satellites;
                            mSatelliteAdapter.notifyDataSetChanged();
                        }
                    }
                    break;
                case LocationService2.GNSS_PROVIDER_DISABLED_CODE:
                    DialogUtils.showLocationSettingsAlert(GnssSkyViewActivity.this);
                    break;
            }
        }
    };

    private final ServiceConnection mLocationServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
        }
    };

    private final BroadcastReceiver mSatelliteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive Satellite");
            String action = intent.getAction();
            Log.d(TAG, action);

            Message msg = Message.obtain();
            if (action.equals(LocationService2.GNSS_SATELLITE_STATUS_CHANGED_ACTION)) {
                List<SatelliteInfo> satellites = intent.getParcelableArrayListExtra("Satellites");
                Log.d(TAG, "Satellite Number: " + satellites.size());
                msg.what = LocationService2.GNSS_SATELLITE_STATUS_CHANGED_CODE;
                msg.obj = satellites;
                mMainHandler.sendMessage(msg);

                // 怀疑Adapter中的操作会导致列表内容变动，采用深拷贝避免
                mSkyView.updateSatellite(satellites.stream().collect(Collectors.toList()));
            }
            else if (action.equals(LocationService2.GNSS_PROVIDER_DISABLED_ACTION)) {
                // TODO::解决退出APP主界面一段时间后的闪退问题
                // TODO::观察退出APP页面后LocationService的变化（logcat日志观察）
                mMainHandler.sendEmptyMessage(LocationService2.GNSS_PROVIDER_DISABLED_CODE);
            }
        }
    };

    private final HandlerThread mSatelliteReceiverThread = new HandlerThread("Satellite Receiver");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gnss_sky_view);
        Log.d(TAG, "onCreate");

        mSkyView = (GnssSkyView) findViewById(R.id.gnssSkyView);
        rvSatellite = (RecyclerView) findViewById(R.id.rvSatellite);
        rvSatellite.setItemAnimator(null);
        rvSatellite.addItemDecoration(new DividerItemDecoration(this,
                DividerItemDecoration.VERTICAL));

        LinearLayoutManager layoutManager = new LinearLayoutManager(GnssSkyViewActivity.this);
        rvSatellite.setLayoutManager(layoutManager);
        rvSatellite.setItemAnimator(null);
        mSatelliteAdapter = new SatelliteAdapter();

        mSatelliteReceiverThread.start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        Intent intent = new Intent(GnssSkyViewActivity.this, LocationService2.class);
        bindService(intent, mLocationServiceConn, BIND_AUTO_CREATE);

        IntentFilter satelliteIntent = new IntentFilter();
        satelliteIntent.addAction(LocationService2.GNSS_SATELLITE_STATUS_CHANGED_ACTION);
        satelliteIntent.addAction(LocationService2.GNSS_PROVIDER_DISABLED_ACTION);
        registerReceiver(mSatelliteReceiver,
                satelliteIntent,
                null,
                new Handler(mSatelliteReceiverThread.getLooper()));
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");

        unregisterReceiver(mSatelliteReceiver);
        unbindService(mLocationServiceConn);

        new Thread(new Runnable() {
            @Override
            public void run() {
                mSkyView.clearSatellite();
            }
        }).start();

        if (rvSatellite.getChildCount() > 0) {
            rvSatellite.removeAllViews();
            mSatelliteAdapter.notifyDataSetChanged();
            mSatellites = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        mSatelliteReceiverThread.quitSafely();
    }

    private class SatelliteAdapter extends RecyclerView.Adapter<SatelliteViewHolder> {

        @NonNull
        @Override
        public SatelliteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
//            View view = View.inflate(GnssSkyViewActivity.this, R.layout.satellite_list_item, null);
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.satellite_list_item, parent, false);
            SatelliteViewHolder viewHolder = new SatelliteViewHolder(view);
            return viewHolder;
        }

        @Override
        public void onBindViewHolder(@NonNull SatelliteViewHolder holder, int position) {
            SatelliteInfo satellite = mSatellites.get(position);
            holder.tvSvid.setText(String.format("%d", satellite.getSvid()));
            holder.ivFlag.setImageResource(satellite.getFlag());
            holder.tvFreq.setText(String.format("%.2fHz", satellite.getCarrierFrequencyHz() / 1_000_000));
            holder.tvCn0.setText(String.format("%.2f", satellite.getCn0DbHz()));
            holder.tvUsed.setText(satellite.isUsed() ? "Y" : " ");
        }

        @Override
        public int getItemCount() {
            return mSatellites != null ? mSatellites.size() : 0;
        }
    }

    private class SatelliteViewHolder extends RecyclerView.ViewHolder {

        protected TextView tvSvid;

        protected ImageView ivFlag;

        protected TextView tvFreq;

        protected TextView tvCn0;

        protected TextView tvUsed;

        public SatelliteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSvid = itemView.findViewById(R.id.tvSvid);
            ivFlag = itemView.findViewById(R.id.ivFlag);
            tvFreq = itemView.findViewById(R.id.tvFreq);
            tvCn0 = itemView.findViewById(R.id.tvCn0);
            tvUsed = itemView.findViewById(R.id.tvUsed);
        }
    }

    private class SatelliteDiffCallback extends DiffUtil.Callback {

        private List<SatelliteInfo> mOlds;

        private List<SatelliteInfo> mNews;

        SatelliteDiffCallback(List<SatelliteInfo> olds, List<SatelliteInfo> news) {
            mOlds = olds;
            mNews = news;
        }

        @Override
        public int getOldListSize() {
            return mOlds != null ? mOlds.size() : 0;
        }

        @Override
        public int getNewListSize() {
            return mNews != null ? mNews.size() : 0;
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return mOlds.get(oldItemPosition).getSvid() == mNews.get(newItemPosition).getSvid();
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return mOlds.get(oldItemPosition).equals(mNews.get(newItemPosition));
        }
    }
}