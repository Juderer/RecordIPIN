package com.zhushuli.recordipin;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
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
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.zhushuli.recordipin.models.cellular.CellPacket;
import com.zhushuli.recordipin.models.cellular.CellNeighbor;
import com.zhushuli.recordipin.models.cellular.CellService;
import com.zhushuli.recordipin.models.cellular.CellServiceLte;
import com.zhushuli.recordipin.models.cellular.CellServiceNr;
import com.zhushuli.recordipin.services.CellularService;
import com.zhushuli.recordipin.services.LocationService;
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
import java.util.concurrent.atomic.AtomicBoolean;

public class CellularActivity extends AppCompatActivity {

    private static final String TAG = CellularActivity.class.getSimpleName();

    // TODO::private static修改为public static，方便其他类引用
    private static final int SERVICE_CELL_INFO_CODE = 1000;
    private static final int SERVICE_CELL_INFO_LTE_CODE = 1001;
    private static final int SERVICE_CELL_INFO_NR_CODE = 1002;
    private static final int NEIGHBOR_CELL_INFO_CODE = 2000;
    private static final int NEIGHBOR_CELL_INFO_LTE_CODE = 2001;
    private static final int NEIGHBOR_CELL_INFO_NR_CODE = 2002;
    private static final int NETWORK_TYPE_UPDATE_CODE = 3000;

    private static final int GNSS_LOCATION_UPDATE_CODE = 8402;
    private static final int GNSS_SEARCHING_CODE = 502;
    private static final int GNSS_PROVIDER_DISABLED_CODE = 404;

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
    private CheckBox cbRecord;

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
    private AtomicBoolean abRecord = new AtomicBoolean(false);

    private final Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case GNSS_LOCATION_UPDATE_CODE:
                    Location location = (Location) msg.obj;
                    tvLocation.setText(String.format("%.6f,%.6f", location.getLongitude(), location.getLatitude()));
                    tvLocationAcc.setText(String.format("%.2fm", location.getAccuracy()));
                    break;
                case SERVICE_CELL_INFO_CODE:
                    if (msg.obj instanceof CellService) {
                        CellService serviceCell = (CellService) msg.obj;
                        tvCid.setText(String.valueOf(serviceCell.getCid()));
                        tvTac.setText(String.valueOf(serviceCell.getTac()));
                        tvServiceEarfcn.setText(String.valueOf(serviceCell.getEarfcn()));
                        tvServicePci.setText(String.valueOf(serviceCell.getPci()));
                        if (serviceCell instanceof CellServiceLte) {
                            tvServiceTa.setText(String.valueOf(((CellServiceLte) serviceCell).getTa()));
                        }
                        tvServiceRsrp.setText(String.valueOf(serviceCell.getRsrp()));
                        tvServiceRsrq.setText(String.valueOf(serviceCell.getRsrq()));

                        // TODO::偶尔会出现不准确！？保守起见选择注释
//                        tvMcc.setText(serviceCell.getMcc());
//                        tvMnc.setText(serviceCell.getMnc());
                    }
                    if (msg.obj instanceof CellServiceLte) {
                        tvNetworkTypeName.setText("LTE");
                    }
                    else if (msg.obj instanceof CellServiceNr) {
                        tvNetworkTypeName.setText("NR");
                    }
                    else {
                        tvNetworkTypeName.setText("UNKNOWN");
                    }
                    break;
                case SERVICE_CELL_INFO_LTE_CODE:
                    break;
                case SERVICE_CELL_INFO_NR_CODE:
                    break;
                case NEIGHBOR_CELL_INFO_CODE:
                    initNeighborTable();

                    List<TableRow> neighborRows = (List<TableRow>) msg.obj;
                    for (TableRow row : neighborRows) {
                        neighborTable.addView(row);
                    }
                    break;
                case NEIGHBOR_CELL_INFO_LTE_CODE:
                    break;
                case NEIGHBOR_CELL_INFO_NR_CODE:
                    break;
                case NETWORK_TYPE_UPDATE_CODE:
                    int networkType = (int) msg.obj;
                    switch (networkType) {
                        case TelephonyManager.NETWORK_TYPE_NR:
                            tvNetworkTypeName.setText("NR");
                            break;
                        case TelephonyManager.NETWORK_TYPE_LTE:
                            tvNetworkTypeName.setText("LTE");
                            break;
                        default:
                            tvNetworkTypeName.setText("UNKNOWN");
                            break;
                    }
                    break;
                default:
                    break;
            }
        }
    };

    // 蜂窝网络服务相关类
    private CellularService.MyBinder mCellularBinder = null;
    private CellularService mCellularService;
    private ServiceConnection mCellularServiceConn;

    // 定位服务相关类
    private LocationService.MyBinder mLocationBinder = null;
    private LocationService mLocationService;
    private ServiceConnection mLocationServiceConn;

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
    }

    @SuppressLint("MissingPermission")
    private void initMobileNetworkInfo() {
        String mOperatorName = CellularUtils.getOperatorName(this);
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
                    bindService();
                }
                else {
                    unbindService();
                    etPoi.setText("");
                    etPoi.clearFocus();
                }
            }
        });

        cbRecord = (CheckBox) findViewById(R.id.cbRecord);
        cbRecord.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    abRecord.set(true);
                } else {
                    abRecord.set(false);
                }
                Log.d(TAG, "onCheckedChanged");
            }
        });

        etPoi = (EditText) findViewById(R.id.etPoi);
        btnPoiSave = (Button) findViewById(R.id.btnPoiSave);
        btnPoiSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick:" + ThreadUtils.threadID());
                if (!abRecord.get() || btnCellularTrack.getText() == "Tracking" || mRecordAbsDir == null) {
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
            tv.setText("邻基站");
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

    private List<TableRow> genNeighborRow(List<CellPacket> neighborCells) {
        List<TableRow> neighborRows = new ArrayList<>();
        for (CellPacket cell : neighborCells) {
            TableRow row = new TableRow(this);
            row.setDividerDrawable(getDrawable(R.drawable.line_h));
            row.setShowDividers(TableRow.SHOW_DIVIDER_BEGINNING | TableRow.SHOW_DIVIDER_MIDDLE | TableRow.SHOW_DIVIDER_END);
            row.setOrientation(LinearLayout.HORIZONTAL);

            CellNeighbor neighbor = (CellNeighbor) cell;
            if (neighbor != null) {
                addValueToRow(row, String.valueOf(neighbor.getEarfcn()));
                addValueToRow(row, String.valueOf(neighbor.getPci()));
                addValueToRow(row, String.valueOf(neighbor.getRsrp()));
                addValueToRow(row, String.valueOf(neighbor.getRsrq()));
            }

            neighborRows.add(row);
        }
        return neighborRows;
    }

    private void bindService() {
        btnCellularTrack.setText("Stop");
        cbRecord.setEnabled(false);

        // 初始化服务连接
        initServiceConnection();

        // 绑定蜂窝网络服务
        startService(new Intent(CellularActivity.this, CellService.class));
        Intent cellularIntent = new Intent(CellularActivity.this, CellularService.class);
        bindService(cellularIntent, mCellularServiceConn, BIND_AUTO_CREATE);

        // 绑定定位服务
        Intent locationIntent = new Intent(CellularActivity.this, LocationService.class);
        bindService(locationIntent, mLocationServiceConn, BIND_AUTO_CREATE);
    }

    private void unbindService() {
        btnCellularTrack.setText("Tracking");
        cbRecord.setEnabled(true);

        // 解绑服务
        stopService(new Intent(CellularActivity.this, CellService.class));
        unbindService(mCellularServiceConn);
        unbindService(mLocationServiceConn);

        tvLocation.setText("--");
        tvLocationAcc.setText("--");
        initServiceCell();
        initNeighborTable();
    }

    private void initServiceConnection() {
        Log.d(TAG, "initServiceConnection");

        mRecordAbsDir = mRecordRootDir + File.separator + formatter.format(new Date(System.currentTimeMillis()));

        mCellularServiceConn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "onServiceConnected, Cellular");
                mCellularBinder = (CellularService.MyBinder) service;
                mCellularService = mCellularBinder.getCellularService();

                if (abRecord.get()) {
                    mCellularService.startCellularRecording(mRecordAbsDir);
                }

                mCellularService.setCallback(new CellularService.Callback() {
                    @Override
                    public void onCellInfoChanged(List<CellPacket> recordCells) {
                        Log.d(TAG, "onCellInfoChanged" + recordCells.size());

                        List<CellPacket> neighborCells = new ArrayList<>();
                        Message msg;

                        for (CellPacket record : recordCells) {
                            if (record instanceof CellService) {
                                msg = Message.obtain();
                                msg.what = SERVICE_CELL_INFO_CODE;
                                msg.obj = record;
                                mMainHandler.sendMessage(msg);
                            } else {
                                neighborCells.add(record);
                            }
                        }

                        msg = Message.obtain();
                        msg.obj = genNeighborRow(neighborCells);
                        msg.what = NEIGHBOR_CELL_INFO_CODE;
                        mMainHandler.sendMessage(msg);
                    }

                    @Override
                    public void onDataConnectionStateChanged(int state, int networkType) {
                        Log.d(TAG, "onDataConnectionStateChanged");
                        Log.d(TAG, CellularUtils.getMobileNetworkTypeName(CellularActivity.this));

                        Message msg = Message.obtain();
                        msg.what = NETWORK_TYPE_UPDATE_CODE;
                        msg.obj = networkType;
                        mMainHandler.sendMessage(msg);
                    }
                });
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };

         mLocationServiceConn = new ServiceConnection(){

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "onServiceConnected, Location");
                mLocationBinder = (LocationService.MyBinder) service;
                mLocationService = mLocationBinder.getLocationService();

                if (abRecord.get()) {
                    mLocationService.startLocationRecording(mRecordAbsDir);
                }

                mLocationService.setCallback(new LocationService.Callback() {
                    @Override
                    public void onLocationChanged(Location location) {
                        Log.d(TAG, "onLocationChanged");
                        Message msg = Message.obtain();
                        msg.what = GNSS_LOCATION_UPDATE_CODE;
                        msg.obj = location;
                        mMainHandler.sendMessage(msg);
                    }

                    @Override
                    public void onLocationProvoiderDisabled() {
                        Log.d(TAG, "onLocationProvoiderDisabled");
                    }

                    @Override
                    public void onLocationSearching(String data) {
                        Log.d(TAG, "onLocationSearching");
                    }
                });
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "onServiceDisconnected");
            }
        };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        if (btnCellularTrack.getText().equals("Stop")) {
            unbindService();
        }
    }
}