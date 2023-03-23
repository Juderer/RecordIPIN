package com.zhushuli.recordipin.models.cellular;

import android.os.Build;
import android.os.SystemClock;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoNr;
import android.telephony.CellSignalStrengthNr;

import androidx.annotation.RequiresApi;

@Deprecated
public class CellNeighborNr extends CellNeighbor {

    public CellNeighborNr() {
        super();
    }

    public CellNeighborNr(int earfcn, int pci, int rsrp, int rsrq) {
        super(earfcn, pci, rsrp, rsrq);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public CellNeighborNr(CellInfo cell) {
        if (cell instanceof CellInfoNr && !cell.isRegistered()) {
            recordFromCellInfoNr((CellInfoNr) cell);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void recordFromCellInfoNr(CellInfoNr cellInfoNr) {
        CellIdentityNr identityNr = (CellIdentityNr) cellInfoNr.getCellIdentity();
        CellSignalStrengthNr cssNr = (CellSignalStrengthNr) cellInfoNr.getCellSignalStrength();

        setEarfcn(identityNr.getNrarfcn());
        setPci(identityNr.getPci());

        setRsrp(cssNr.getSsRsrp());
        setRsrq(cssNr.getSsRsrq());

        // 记录时间戳
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setTimeStamp(System.currentTimeMillis() + (cellInfoNr.getTimestampMillis() - SystemClock.elapsedRealtime()));
        }
        else {
            long ts = System.currentTimeMillis() +
                    Math.round((cellInfoNr.getTimeStamp() - SystemClock.elapsedRealtimeNanos()) / 1000000.0);
            setTimeStamp(ts);
        }
    }

    @Override
    public void setServiceCell(CellService serviceCell) {
        return ;
    }
}
