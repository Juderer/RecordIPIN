package com.zhushuli.recordipin.model.cellular;

import android.os.Build;
import android.os.SystemClock;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;

import com.zhushuli.recordipin.utils.TimeReferenceUtils;

public class CellNeighborLte extends CellNeighbor {

    public CellNeighborLte() {
        super();
    }

    public CellNeighborLte(int earfcn, int pci, int rsrp, int rsrq) {
        super(earfcn, pci, rsrp, rsrq);
    }

    public CellNeighborLte(CellInfo cell) {
        if (cell instanceof CellInfoLte && !cell.isRegistered()) {
            recordFromCellInfoLte((CellInfoLte) cell);
        }
    }

    private void recordFromCellInfoLte(CellInfoLte cellInfoLte) {
        CellIdentityLte identityLte = cellInfoLte.getCellIdentity();
        CellSignalStrengthLte cssLte = cellInfoLte.getCellSignalStrength();

        setEarfcn(identityLte.getEarfcn());
        setPci(identityLte.getPci());

        setRsrp(cssLte.getRsrp());
        setRsrq(cssLte.getRsrq());

        // 记录时间戳
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setTimeStamp(System.currentTimeMillis() + (cellInfoLte.getTimestampMillis() - SystemClock.elapsedRealtime()));
        }
        else {
            long ts = System.currentTimeMillis() +
                    Math.round((cellInfoLte.getTimeStamp() - SystemClock.elapsedRealtimeNanos()) / 1000000.0);
            setTimeStamp(ts);
        }
    }

    @Override
    public void setServiceCell(CellService serviceCell) {
        return ;
    }
}
