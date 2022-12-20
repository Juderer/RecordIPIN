package com.zhushuli.recordipin;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.zhushuli.recordipin.model.cellular.CellNeighborNr;
import com.zhushuli.recordipin.model.cellular.CellPacket;
import com.zhushuli.recordipin.model.cellular.CellNeighbor;
import com.zhushuli.recordipin.model.cellular.CellService;
import com.zhushuli.recordipin.model.cellular.CellServiceLte;
import com.zhushuli.recordipin.model.cellular.CellServiceNr;
import com.zhushuli.recordipin.service.CellularService;
import com.zhushuli.recordipin.utils.CellularUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CellularActivity extends AppCompatActivity {

    private static final String TAG = CellularActivity.class.getSimpleName();

    private static final int SERVICE_CELL_INFO_CODE = 1000;
    private static final int SERVICE_CELL_INFO_LTE_CODE = 1001;
    private static final int SERVICE_CELL_INFO_NR_CODE = 1002;
    private static final int NEIGHBOR_CELL_INFO_CODE = 2000;
    private static final int NEIGHBOR_CELL_INFO_LTE_CODE = 2001;
    private static final int NEIGHBOR_CELL_INFO_NR_CODE = 2002;
    private static final int NETWORK_TYPE_UPDATE_CODE = 3000;

    private TextView tvOperatorName;
    private TextView tvNetworkTypeName;
    private TextView tvMcc;
    private TextView tvMnc;
    private TextView tvCid;
    private TextView tvTac;
    private TextView tvServiceEarfcn;
    private TextView tvServicePci;
    private TextView tvServiceRsrp;
    private TextView tvServiceRsrq;
    private TableLayout neighborTable;
    private Button btnCellularTrack;
    private CheckBox cbRecord;

    private String mOperatorName;
    private String mNetworkTypeName;
    private String mMcc;
    private String mMnc;

    private List<TableRow> mNgbTableHeader = new ArrayList<>();
    private AtomicBoolean abNgbHeader = new AtomicBoolean(false);

    private ServiceConnection mCellularServiceConn;
    private CellularService.MyBinder mBinder = null;
    private CellularService mCellularService;

    // 保存路径
    private SimpleDateFormat formatter;
    private String mRecordingDir;
    private AtomicBoolean abRecording = new AtomicBoolean(true);

    private Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case SERVICE_CELL_INFO_CODE:
                    if (msg.obj instanceof CellService) {
                        CellService serviceCell = (CellService) msg.obj;
                        tvCid.setText(String.valueOf(serviceCell.getCid()));
                        tvTac.setText(String.valueOf(serviceCell.getTac()));
                        tvServiceEarfcn.setText(String.valueOf(serviceCell.getEarfcn()));
                        tvServicePci.setText(String.valueOf(serviceCell.getPci()));
                        tvServiceRsrp.setText(String.valueOf(serviceCell.getRsrp()));
                        tvServiceRsrq.setText(String.valueOf(serviceCell.getRsrq()));
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
//                            tvNetworkTypeName.setText("UNKNOWN");
                            tvNetworkTypeName.setText(CellularUtils.getMobileNetworkTypeName(CellularActivity.this));
                            break;
                    }
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cellular);
        Log.d(TAG, "onCreate");

        initView();
        initServiceCell();
        initNeighborTable();
        initMobileNetworkInfo();

        formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        mRecordingDir = getExternalFilesDir(
                Environment.getDataDirectory().getAbsolutePath()).getAbsolutePath();
    }

    @SuppressLint("MissingPermission")
    private void initMobileNetworkInfo() {
        // TODO::设置监听，捕获移动网络类型变化（主要是4G与5G的切换）
        // 暂未解决连接WiFi时的无法准确获取移动信号类型的问题
        mOperatorName = CellularUtils.getOperatorName(this);
//        mNetworkTypeName = CellularUtils.getMobileNetworkTypeName(CellularActivity.this);
        mMcc = CellularUtils.getMoblieCountryCode(this);
        mMnc = CellularUtils.getMobileNetworkCode(this);

        tvOperatorName.setText(mOperatorName);
//        tvNetworkTypeName.setText(mNetworkTypeName);
        tvMcc.setText(mMcc);
        tvMnc.setText(mMnc);
    }

    private void initView() {
        tvOperatorName = (TextView) findViewById(R.id.tvOperatorName);
        tvNetworkTypeName = (TextView) findViewById(R.id.tvMobileNetTypeName);
        tvMcc = (TextView) findViewById(R.id.tvMcc);
        tvMnc = (TextView) findViewById(R.id.tvMnc);

        tvCid = (TextView) findViewById(R.id.tvCid);
        tvTac = (TextView) findViewById(R.id.tvTac);
        tvServiceEarfcn = (TextView) findViewById(R.id.tvServiceEarfcn);
        tvServicePci = (TextView) findViewById(R.id.tvServicePci);
        tvServiceRsrp = (TextView) findViewById(R.id.tvServiceRsrp);
        tvServiceRsrq = (TextView) findViewById(R.id.tvServiceRsrq);

        neighborTable = (TableLayout) findViewById(R.id.tabNeighborCell);

        btnCellularTrack = (Button) findViewById(R.id.btnCellularTrack);
        btnCellularTrack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnCellularTrack.getText().equals("Tracking")) {
                    bindService();
                    btnCellularTrack.setText("Stop");
                    cbRecord.setEnabled(false);
                }
                else {
                    unbindService();
                    btnCellularTrack.setText("Tracking");
                    cbRecord.setEnabled(true);
                }
            }
        });

        cbRecord = (CheckBox) findViewById(R.id.cbRecord);
        cbRecord.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    abRecording.set(true);
                } else {
                    abRecording.set(false);
                }
                Log.d(TAG, "onCheckedChanged");
            }
        });
    }

    private void initServiceCell() {
        tvNetworkTypeName.setText("--");
        tvCid.setText("--");
        tvTac.setText("--");
        tvServiceEarfcn.setText("--");
        tvServicePci.setText("--");
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
        // 初始化服务连接
        initServiceConnection();

        // 绑定蜂窝网络服务
        Intent cellularIntent = new Intent(CellularActivity.this, CellularService.class);
        bindService(cellularIntent, mCellularServiceConn, BIND_AUTO_CREATE);
    }

    private void unbindService() {
        // 解绑服务
        unbindService(mCellularServiceConn);

        initServiceCell();
        initNeighborTable();
    }

    private void initServiceConnection() {
        Log.d(TAG, "initServiceConnection");

        mCellularServiceConn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "onServiceConnected");
                mBinder = (CellularService.MyBinder) service;
                mCellularService = mBinder.getCellularService();

                if (abRecording.get()) {
                    String recordingDir = mRecordingDir + File.separator + formatter.format(new Date(System.currentTimeMillis()));
                    mCellularService.startCellularRecording(recordingDir);
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