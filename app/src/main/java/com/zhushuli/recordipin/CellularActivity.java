package com.zhushuli.recordipin;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.zhushuli.recordipin.model.RecordNeighbor;
import com.zhushuli.recordipin.model.RecordNeighborLte;
import com.zhushuli.recordipin.model.RecordNeighborNr;
import com.zhushuli.recordipin.model.RecordService;
import com.zhushuli.recordipin.model.RecordServiceLte;
import com.zhushuli.recordipin.model.RecordServiceNr;
import com.zhushuli.recordipin.utils.CellularUtils;
import com.zhushuli.recordipin.utils.NetworkUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class CellularActivity extends AppCompatActivity {

    private static final String TAG = CellularActivity.class.getSimpleName();

    private static final int SERVICE_CELL_INFO_CODE = 1000;
    private static final int SERVICE_CELL_INFO_LTE_CODE = 1001;
    private static final int SERVICE_CELL_INFO_NR_CODE = 1002;
    private static final int NEIGHBOR_CELL_INFO_CODE = 2000;
    private static final int NEIGHBOR_CELL_INFO_LTE_CODE = 2001;
    private static final int NEIGHBOR_CELL_INFO_NR_CODE = 2002;

    private TelephonyManager mTelephonyManager;
    private MyTelephonyCallback mTelephonyCallback;
    private MyPhoneStateListener mPhoneStateListener;

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

    private String mOperatorName;
    private String mNetworkTypeName;
    private String mMcc;
    private String mMnc;

    private List<TableRow> mNgbTableHeader = new ArrayList<>();
    private AtomicBoolean abNgbHeader = new AtomicBoolean(false);

    private Handler mMainHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case SERVICE_CELL_INFO_CODE:
                    if (msg.obj instanceof RecordService) {
                        RecordService serviceCell = (RecordService) msg.obj;
                        tvCid.setText(String.valueOf(serviceCell.getCid()));
                        tvTac.setText(String.valueOf(serviceCell.getTac()));
                        tvServiceEarfcn.setText(String.valueOf(serviceCell.getEarfcn()));
                        tvServicePci.setText(String.valueOf(serviceCell.getPci()));
                        tvServiceRsrp.setText(String.valueOf(serviceCell.getRsrp()));
                        tvServiceRsrq.setText(String.valueOf(serviceCell.getRsrq()));
                    }
                    break;
                case SERVICE_CELL_INFO_LTE_CODE:
                    break;
                case SERVICE_CELL_INFO_NR_CODE:
                    break;
                case NEIGHBOR_CELL_INFO_CODE:
                    neighborTable.removeAllViews();

                    genNeighborTableHeader();
                    for (TableRow row : mNgbTableHeader) {
                        neighborTable.addView(row);
                    }

                    List<TableRow> neighborRows = (List<TableRow>) msg.obj;
                    for (TableRow row : neighborRows) {
                        neighborTable.addView(row);
                    }
                    break;
                case NEIGHBOR_CELL_INFO_LTE_CODE:
                    break;
                case NEIGHBOR_CELL_INFO_NR_CODE:
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_celluar);
        Log.d(TAG, "onCreate");

        initTextView();

        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mTelephonyCallback = new MyTelephonyCallback();
        } else {
            Log.d(TAG, "mPhoneStateListener");
            mPhoneStateListener = new MyPhoneStateListener();
        }

        if (!NetworkUtils.hasSimCard(mTelephonyManager)) {
            Toast.makeText(this, "用户未插SIM卡", Toast.LENGTH_SHORT);
            onDestroy();
        }

        initMobileNetworkInfo();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.d(TAG, "SDK_INT, S");
            mTelephonyManager.registerTelephonyCallback(new Executor() {
                @Override
                public void execute(Runnable command) {
                    Log.d(TAG, "execute");
                    command.run();
                }
            }, mTelephonyCallback);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d(TAG, "SDK_INT, Q");
            // TODO::发现在xiaomi手机上使用需要打开"位置信息"
            mTelephonyManager.listen(mPhoneStateListener,
                    PhoneStateListener.LISTEN_CELL_INFO | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        } else {
            Log.d(TAG, "pass");
        }
    }

    @SuppressLint("MissingPermission")
    private void initMobileNetworkInfo() {
        // TODO::设置监听，捕获移动网络类型变化（主要是4G与5G的切换）
        // 暂未解决连接WiFi时的无法准确获取移动信号类型的问题
        mOperatorName = CellularUtils.getOperatorName(mTelephonyManager);
        mNetworkTypeName = CellularUtils.getMobileNetworkTypeName(CellularActivity.this);
        mMcc = CellularUtils.getMoblieCountryCode(mTelephonyManager);
        mMnc = CellularUtils.getMobileNetworkCode(mTelephonyManager);

        tvOperatorName.setText(mOperatorName);
        tvNetworkTypeName.setText(mNetworkTypeName);
        tvMcc.setText(mMcc);
        tvMnc.setText(mMnc);
    }

    private void initTextView() {
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
        genNeighborTableHeader();
        for (TableRow row : mNgbTableHeader) {
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

    private void genNeighborTableHeader() {
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
    }

    private List<TableRow> genNeighborRow(List<CellInfo> cellInfos) {
        List<TableRow> neighborRows = new ArrayList<>();
        for (CellInfo cell : cellInfos) {
            TableRow row = new TableRow(this);
            row.setDividerDrawable(getDrawable(R.drawable.line_h));
            row.setShowDividers(TableRow.SHOW_DIVIDER_BEGINNING | TableRow.SHOW_DIVIDER_MIDDLE | TableRow.SHOW_DIVIDER_END);
            row.setOrientation(LinearLayout.HORIZONTAL);

            RecordNeighbor neighbor = null;
            if (cell instanceof CellInfoLte) {
                neighbor = new RecordNeighborLte(cell);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (cell instanceof CellInfoNr) {
                    neighbor = new RecordNeighborNr(cell);
                }
            }

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

//    @SuppressLint("ResourceType")
//    private void initNeighborTable() {
//        final Context context = getBaseContext();
//        final TableRow tbRow = new TableRow(context);
////        tbRow.setDividerDrawable(getResources().getDrawable(R.drawable.line_h, null));
////        tbRow.setOrientation(TableRow.HORIZONTAL);
////        tbRow.setShowDividers(TableRow.SHOW_DIVIDER_BEGINNING | TableRow.SHOW_DIVIDER_MIDDLE | TableRow.SHOW_DIVIDER_END);
////        tbRow.removeAllViews();
//        final TextView tvNgbEarfcn = new TextView(CellularActivity.this);
//        tvNgbEarfcn.setText("EARFCN");
//        tvNgbEarfcn.setTextSize(16);
//        tvNgbEarfcn.setGravity(Gravity.CENTER);
//        tvNgbEarfcn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
//        tbRow.addView(tvNgbEarfcn);
//        TextView tvNgbPci = new TextView(CellularActivity.this);
//        tvNgbPci.setText("PCI");
//        tvNgbPci.setTextSize(16);
//        tvNgbPci.setGravity(Gravity.CENTER);
//        tvNgbPci.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
////        tbRow.addView(tvNgbPci);
//        TextView tvNgbRsrp = new TextView(CellularActivity.this);
//        tvNgbRsrp.setText("RSRP");
//        tvNgbRsrp.setTextSize(16);
//        tvNgbRsrp.setGravity(Gravity.CENTER);
//        tvNgbRsrp.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
////        tbRow.addView(tvNgbRsrp);
//        TextView tvNgbRsrq = new TextView(CellularActivity.this);
//        tvNgbRsrq.setText("RSRQ");
//        tvNgbRsrq.setTextSize(16);
//        tvNgbRsrq.setGravity(Gravity.CENTER);
//        tvNgbRsrq.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
////        tbRow.addView(tvNgbRsrq);
//        tlNeighborCell.removeAllViews();
//        tlNeighborCell.addView(tvNgbRsrp);
//        tlNeighborCell.addView(tvNgbRsrq);
//        tlNeighborCell.addView(tbRow);
//    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private class MyTelephonyCallback extends TelephonyCallback implements TelephonyCallback.CellInfoListener {
        private Message mMsg;
        private List<CellInfo> neighborCells = new ArrayList<>();

        public void onCellInfoChanged(@NonNull List<CellInfo> cellInfo) {
            Log.d(TAG, "onCellInfoChanged" + cellInfo.size());

            neighborCells.clear();
            for (CellInfo cell : cellInfo) {
                if (cell.isRegistered()) {
                    mMsg = Message.obtain();
                    mMsg.what = SERVICE_CELL_INFO_CODE;

                    if (cell instanceof CellInfoLte) {
                        mMsg.obj = new RecordServiceLte(cell);
                    }
                    else if (cell instanceof CellInfoNr) {
                        mMsg.obj = new RecordServiceNr(cell);
                    }
                    else {
                        mMsg.obj = null;
                    }
                    mMainHandler.sendMessage(mMsg);
                } else {
                    neighborCells.add(cell);
                }
            }

            mMsg = Message.obtain();
            mMsg.obj = genNeighborRow(neighborCells);
            mMsg.what = NEIGHBOR_CELL_INFO_CODE;
            mMainHandler.sendMessage(mMsg);
        }
    }

    @SuppressLint("MissingPermission")
    private class MyPhoneStateListener extends PhoneStateListener {
        private Message mMsg;
        private List<CellInfo> neighborCells = new ArrayList<>();

        @Override
        @RequiresApi(api = Build.VERSION_CODES.Q)
        public void onCellInfoChanged(List<CellInfo> cellInfo) {
            super.onCellInfoChanged(cellInfo);
            Log.d(TAG, "onCellInfoChanged" + cellInfo.size());

            neighborCells.clear();
            for (CellInfo cell : cellInfo) {
                if (cell.isRegistered()) {
                    mMsg = Message.obtain();
                    mMsg.what = SERVICE_CELL_INFO_CODE;

                    if (cell instanceof CellInfoLte) {
                        mMsg.obj = new RecordServiceLte(cell);
                    }
                    else if (cell instanceof CellInfoNr) {
                        mMsg.obj = new RecordServiceNr(cell);
                    }
                    else {
                        mMsg.obj = null;
                    }
                    mMainHandler.sendMessage(mMsg);
                } else {
                    neighborCells.add(cell);
                }
            }

            mMsg = Message.obtain();
            mMsg.obj = genNeighborRow(neighborCells);
            mMsg.what = NEIGHBOR_CELL_INFO_CODE;
            mMainHandler.sendMessage(mMsg);
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            super.onSignalStrengthsChanged(signalStrength);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (mTelephonyManager instanceof TelephonyManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mTelephonyManager.unregisterTelephonyCallback(mTelephonyCallback);
                Log.d(TAG, ">=S unregister");
            } else {
                mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
                Log.d(TAG, "<S unregister");
            }
        }
    }
}