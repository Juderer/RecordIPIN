package com.zhushuli.recordipin.activities.cellular;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.zhushuli.recordipin.LocationActivity;
import com.zhushuli.recordipin.R;
import com.zhushuli.recordipin.models.cellular.CellNeighborLte;
import com.zhushuli.recordipin.models.cellular.CellNeighborNr;
import com.zhushuli.recordipin.models.cellular.CellPacket;
import com.zhushuli.recordipin.models.cellular.CellNeighbor;
import com.zhushuli.recordipin.models.cellular.CellService;
import com.zhushuli.recordipin.models.cellular.CellServiceLte;
import com.zhushuli.recordipin.models.cellular.CellServiceNr;
import com.zhushuli.recordipin.services.CellularService2;
import com.zhushuli.recordipin.services.LocationService;
import com.zhushuli.recordipin.services.LocationService2;
import com.zhushuli.recordipin.utils.CellularUtils;
import com.zhushuli.recordipin.utils.DialogUtils;
import com.zhushuli.recordipin.utils.FileUtils;
import com.zhushuli.recordipin.utils.ThreadUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CellularActivity extends AppCompatActivity {

    private static final String TAG = CellularActivity.class.getSimpleName();

    private TextView tvLocation;
    private TextView tvLocationAcc;
    // 蜂窝网络相关组件
    private TextView tvOperatorName;
    private TextView tvNetworkTypeName;
    private TextView tvMcc;
    private TextView tvMnc;
    private TextView tvCid;
    private TextView tvTac;
    private TextView tvServiceEarfcn;
    private TextView tvServicePci;
    private TextView tvServiceTa;
    private TextView tvServiceRsrp;
    private TextView tvServiceRsrq;
    private TableLayout neighborTable;

    private Button btnCellularTrack;

    // POI信息（室内采集时辅助确认真值）
    private EditText etPoi;
    // 保存POI信息
    private Button btnPoiSave;
    // POI列表
    private List<String> pois = new ArrayList<>();

    private List<TableRow> mNgbTableHeader = new ArrayList<>();
    private AtomicBoolean abNgbHeader = new AtomicBoolean(false);

    // 保存路径
    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
    private String mRecordRootDir;
    private String mRecordAbsDir;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper()) {

        private void updateServingCell(CellInfo cell) {
            if (cell instanceof CellInfoLte) {
                CellInfoLte cellInfoLte = (CellInfoLte) cell;
                tvNetworkTypeName.setText("LTE");

                CellIdentityLte identityLte = cellInfoLte.getCellIdentity();
                tvCid.setText(String.valueOf(identityLte.getCi()));
                tvTac.setText(String.valueOf(identityLte.getTac()));
                tvServiceEarfcn.setText(String.valueOf(identityLte.getEarfcn()));
                tvServicePci.setText(String.valueOf(identityLte.getPci()));

                CellSignalStrengthLte signalStrengthLte = cellInfoLte.getCellSignalStrength();
                tvServiceTa.setText(String.valueOf(signalStrengthLte.getTimingAdvance()));
                tvServiceRsrp.setText(String.valueOf(signalStrengthLte.getRsrp()));
                tvServiceRsrq.setText(String.valueOf(signalStrengthLte.getRsrq()));
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (cell instanceof CellInfoNr) {
                    Log.d(TAG, cell.toString());
                    CellInfoNr cellInfoNr = (CellInfoNr) cell;
                    tvNetworkTypeName.setText("NR");

                    CellIdentityNr identityNr = (CellIdentityNr) cellInfoNr.getCellIdentity();
                    tvCid.setText(String.valueOf(identityNr.getNci()));
                    tvTac.setText(String.valueOf(identityNr.getTac()));
                    tvServiceEarfcn.setText(String.valueOf(identityNr.getNrarfcn()));
                    tvServicePci.setText(String.valueOf(identityNr.getPci()));

                    CellSignalStrengthNr signalStrengthNr = (CellSignalStrengthNr) cellInfoNr.getCellSignalStrength();
                    tvServiceTa.setText(String.valueOf(CellInfo.UNAVAILABLE));
                    tvServiceRsrp.setText(String.valueOf(signalStrengthNr.getSsRsrp()));
                    tvServiceRsrq.setText(String.valueOf(signalStrengthNr.getSsRsrq()));
                }
            }
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case LocationService2.GNSS_LOCATION_CHANGED_CODE:
                    Location location = (Location) msg.obj;
                    tvLocation.setText(String.format("%.6f,%.6f", location.getLongitude(), location.getLatitude()));
                    tvLocationAcc.setText(String.format("%.2fm", location.getAccuracy()));
                    break;
                case LocationService2.GNSS_PROVIDER_DISABLED_CODE:
                    DialogUtils.showLocationSettingsAlert(CellularActivity.this);
                    unbindServices();
                    break;
                case CellularService2.CELLULAR_CELL_INFO_CHANGED_CODE:
                    List<CellInfo> cellInfo = (List<CellInfo>) msg.obj;

                    initNeighborTable();
                    for (CellInfo cell : cellInfo) {
                        if (cell.isRegistered()) {
                            updateServingCell(cell);
                        } else {
                            TableRow row = genNeighborRow(cell);
                            if (row != null) {
                                neighborTable.addView(row);
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    };

    // 定位服务相关类
    private LocationService2.LocationBinder mLocationBinder = null;

    private LocationService2 mLocationService2;

    private final ServiceConnection mLocationServiceConn = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected, Location");
            mLocationBinder = (LocationService2.LocationBinder) service;
            mLocationService2 = mLocationBinder.getLocationService2();

            mLocationService2.startLocationRecord(mRecordAbsDir);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
        }
    };

    private final BroadcastReceiver mLocationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive Location");
            String action = intent.getAction();
            Log.d(TAG, action);

            Message msg = Message.obtain();
            if (action.equals(LocationService2.GNSS_LOCATION_CHANGED_ACTION)) {
                Location location = intent.getParcelableExtra("Location");
                msg.what = LocationService2.GNSS_LOCATION_CHANGED_CODE;
                msg.obj = location;
                mMainHandler.sendMessage(msg);
            } else if (action.equals(LocationService2.GNSS_PROVIDER_DISABLED_ACTION)) {
                mMainHandler.sendEmptyMessage(LocationService2.GNSS_PROVIDER_DISABLED_CODE);
            }
        }
    };

    private final HandlerThread mLocationReceiverThread = new HandlerThread("Location Receiver");

    // 蜂窝网络服务相关类
    private CellularService2.CellularBinder mCellularBinder = null;

    private CellularService2 mCellularService2;

    private final ServiceConnection mCellularServiceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected, Cellular");
            mCellularBinder = (CellularService2.CellularBinder) service;
            mCellularService2 = mCellularBinder.getCellularService2();

            mCellularService2.startCellularRecord(mRecordAbsDir);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };;

    private final BroadcastReceiver mCellularReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive Cellular");
            String action = intent.getAction();
            Log.d(TAG, action);

            if (action.equals(CellularService2.CELLULAR_CELL_INFO_CHANGED_ACTION)) {
                List<CellInfo> cellInfo = intent.getParcelableArrayListExtra("CellInfo");
                for (CellInfo cell : cellInfo) {
                    Log.d(TAG, cell.toString());
                }

                Message msg = Message.obtain();
                msg.obj = cellInfo;
                msg.what = CellularService2.CELLULAR_CELL_INFO_CHANGED_CODE;
                mMainHandler.sendMessage(msg);
            }
        }
    };

    private final HandlerThread mCellularReceiverThread = new HandlerThread("Cellular Receiver");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cellular);
        Log.d(TAG, "onCreate:" + ThreadUtils.threadID());

        initView();
        initServiceCell();
        initNeighborTable();
        initMobileNetworkInfo();

        mRecordRootDir = getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();

        mLocationReceiverThread.start();
        mCellularReceiverThread.start();
    }

    @SuppressLint("MissingPermission")
    private void initMobileNetworkInfo() {
//        String mOperatorName = CellularUtils.getOperatorName(this);
        String mOperatorName = CellularUtils.getOperatorEnglishName(this);
        String mMcc = CellularUtils.getMoblieCountryCode(this);
        String mMnc = CellularUtils.getMobileNetworkCode(this);

        tvOperatorName.setText(mOperatorName);
        tvMcc.setText(mMcc);
        tvMnc.setText(mMnc);
    }

    private void initView() {
        tvLocation = (TextView) findViewById(R.id.tvLocation);
        tvLocationAcc = (TextView) findViewById(R.id.tvLocationAcc);

        tvOperatorName = (TextView) findViewById(R.id.tvOperatorName);
        tvNetworkTypeName = (TextView) findViewById(R.id.tvMobileNetTypeName);
        tvMcc = (TextView) findViewById(R.id.tvMcc);
        tvMnc = (TextView) findViewById(R.id.tvMnc);

        tvCid = (TextView) findViewById(R.id.tvCid);
        tvTac = (TextView) findViewById(R.id.tvTac);
        tvServiceEarfcn = (TextView) findViewById(R.id.tvServiceEarfcn);
        tvServicePci = (TextView) findViewById(R.id.tvServicePci);
        tvServiceTa = (TextView) findViewById(R.id.tvTimingAdvance);
        tvServiceRsrp = (TextView) findViewById(R.id.tvServiceRsrp);
        tvServiceRsrq = (TextView) findViewById(R.id.tvServiceRsrq);

        neighborTable = (TableLayout) findViewById(R.id.tabNeighborCell);

        btnCellularTrack = (Button) findViewById(R.id.btnCellularTrack);
        btnCellularTrack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnCellularTrack.getText().equals("Tracking")) {
                    mRecordAbsDir = mRecordRootDir + File.separator + formatter.format(new Date(System.currentTimeMillis()));
                    bindServices();
                }
                else {
                    unbindServices();
                    etPoi.setText("");
                    etPoi.clearFocus();
                }
            }
        });

        // TODO::will be @Deprecated
        etPoi = (EditText) findViewById(R.id.etPoi);
        btnPoiSave = (Button) findViewById(R.id.btnPoiSave);
        btnPoiSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick:" + ThreadUtils.threadID());
                if (btnCellularTrack.getText() == "Tracking" || mRecordAbsDir == null) {
                    return ;
                }
                if (etPoi.getText().toString().trim().length() > 1) {
                    String poi = etPoi.getText().toString().trim();
                    Log.d(TAG, "onClick:" + poi);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "run:" + ThreadUtils.threadID());
                            boolean exists = FileUtils.checkFileExists(mRecordAbsDir + File.separator + "POI.csv");
                            BufferedWriter writer = null;
                            if (!exists) {
                                writer = FileUtils.initWriter(mRecordAbsDir, "POI.csv");
                            }
                            else {
                                writer = FileUtils.initAppendWriter(mRecordAbsDir, "POI.csv");
                            }
                            try {
                                if (!exists) {
                                    writer.write("sysTime,POI\n");
                                }
                                writer.write(String.format("%d,%s\n", System.currentTimeMillis(), poi));
                                writer.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            FileUtils.closeBufferedWriter(writer);
                        }
                    }).start();
                    Toast.makeText(CellularActivity.this, "POI记录成功", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void initServiceCell() {
        tvNetworkTypeName.setText("--");
        tvCid.setText("--");
        tvTac.setText("--");
        tvServiceEarfcn.setText("--");
        tvServicePci.setText("--");
        tvServiceTa.setText("--");
        tvServiceRsrp.setText("--");
        tvServiceRsrq.setText("--");
    }

    private void initNeighborTable() {
        neighborTable.removeAllViews();
        for (TableRow row : genNeighborTableHeader()) {
            neighborTable.addView(row);
        }
    }

    private void addValueToRow(TableRow row, String value) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(16);
        tv.setGravity(Gravity.CENTER);
        // width=0&weight=1：实现各列宽度一致！！！
        row.addView(tv, new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1.0f));
    }

    private List<TableRow> genNeighborTableHeader() {
        if (!abNgbHeader.getAndSet(true)) {
            TableRow titleRow = new TableRow(this);
            titleRow.setDividerDrawable(getDrawable(R.drawable.line_h));
            titleRow.setShowDividers(TableRow.SHOW_DIVIDER_BEGINNING | TableRow.SHOW_DIVIDER_MIDDLE | TableRow.SHOW_DIVIDER_END);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);

            TextView tv = new TextView(this);
            tv.setText("Neighbors");
            tv.setTextSize(16);
            tv.setGravity(Gravity.LEFT);
            titleRow.addView(tv, new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1.0f));

            TableRow itemRow = new TableRow(this);
            itemRow.setDividerDrawable(getDrawable(R.drawable.line_h));
            itemRow.setShowDividers(TableRow.SHOW_DIVIDER_BEGINNING | TableRow.SHOW_DIVIDER_MIDDLE | TableRow.SHOW_DIVIDER_END);
            itemRow.setOrientation(LinearLayout.HORIZONTAL);

            String[] rowTitles = new String[]{"EARFCN", "PCI", "RSRP", "RSRQ"};
            for (String rowTitle : rowTitles) {
                addValueToRow(itemRow, rowTitle);
            }

            mNgbTableHeader.add(titleRow);
            mNgbTableHeader.add(itemRow);
        }
        return mNgbTableHeader;
    }

    private TableRow genNeighborRow(CellInfo cell) {
        TableRow row = new TableRow(this);
        row.setDividerDrawable(getDrawable(R.drawable.line_h));
        row.setShowDividers(TableRow.SHOW_DIVIDER_BEGINNING | TableRow.SHOW_DIVIDER_MIDDLE | TableRow.SHOW_DIVIDER_END);
        row.setOrientation(LinearLayout.HORIZONTAL);

        if (cell instanceof CellInfoLte) {
            CellInfoLte cellInfoLte = (CellInfoLte) cell;

            CellIdentityLte identityLte = cellInfoLte.getCellIdentity();
            addValueToRow(row, String.valueOf(identityLte.getEarfcn()));
            addValueToRow(row, String.valueOf(identityLte.getPci()));

            CellSignalStrengthLte signalStrengthLte = cellInfoLte.getCellSignalStrength();
            addValueToRow(row, String.valueOf(signalStrengthLte.getRsrp()));
            addValueToRow(row, String.valueOf(signalStrengthLte.getRsrq()));
            return row;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (cell instanceof CellInfoNr) {
                CellInfoNr cellInfoNr = (CellInfoNr) cell;

                CellIdentityNr identityNr = (CellIdentityNr) cellInfoNr.getCellIdentity();
                addValueToRow(row, String.valueOf(identityNr.getNrarfcn()));
                addValueToRow(row, String.valueOf(identityNr.getPci()));

                CellSignalStrengthNr signalStrengthNr = (CellSignalStrengthNr) cellInfoNr.getCellSignalStrength();
                addValueToRow(row, String.valueOf(signalStrengthNr.getSsRsrp()));
                addValueToRow(row, String.valueOf(signalStrengthNr.getSsRsrq()));
                return row;
            }
        }
        return null;
    }

    private void bindServices() {
        btnCellularTrack.setText("Stop");

        // 初始化服务连接
//        initServiceConnection();

        // 绑定蜂窝网络服务
        Intent cellularIntent = new Intent(CellularActivity.this, CellularService2.class);
        bindService(cellularIntent, mCellularServiceConn, BIND_AUTO_CREATE);

        // 绑定定位服务
        Intent locationIntent = new Intent(CellularActivity.this, LocationService2.class);
        bindService(locationIntent, mLocationServiceConn, BIND_AUTO_CREATE);
    }

    private void unbindServices() {
        btnCellularTrack.setText("Tracking");

        // 解绑服务
        unbindService(mCellularServiceConn);
        unbindService(mLocationServiceConn);

        tvLocation.setText("--");
        tvLocationAcc.setText("--");
        initServiceCell();
        initNeighborTable();
    }

    private void initServiceConnection() {
        Log.d(TAG, "initServiceConnection");
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

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        IntentFilter locationIntent = new IntentFilter();
        locationIntent.addAction(LocationService2.GNSS_LOCATION_CHANGED_ACTION);
        locationIntent.addAction(LocationService2.GNSS_PROVIDER_DISABLED_ACTION);
        registerReceiver(mLocationReceiver, locationIntent, null, new Handler(mLocationReceiverThread.getLooper()));

        IntentFilter cellularIntent = new IntentFilter();
        cellularIntent.addAction(CellularService2.CELLULAR_CELL_INFO_CHANGED_ACTION);
        registerReceiver(mCellularReceiver, cellularIntent, null, new Handler(mCellularReceiverThread.getLooper()));
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        unregisterReceiver(mLocationReceiver);
        unregisterReceiver(mCellularReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        if (btnCellularTrack.getText().equals("Stop")) {
            unbindServices();
        }
        mLocationReceiverThread.quitSafely();
        mCellularReceiverThread.quitSafely();
    }
}