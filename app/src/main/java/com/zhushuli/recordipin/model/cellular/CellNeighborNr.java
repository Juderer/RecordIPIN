package com.zhushuli.recordipin.model.cellular;

import android.os.Build;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoNr;
import android.telephony.CellSignalStrengthNr;

import androidx.annotation.RequiresApi;

import com.zhushuli.recordipin.utils.TimeReferenceUtils;

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
        TimeReferenceUtils.setTimeReference(cellInfoNr.getTimeStamp());
        long ts = TimeReferenceUtils.getMyTimeReference() +
                Math.round((cellInfoNr.getTimeStamp() - TimeReferenceUtils.getElapsedTimeReference()) / 1000000.0);
        setTimeStamp(ts);
    }

    @Override
    public void setServiceCell(CellService serviceCell) {
        return ;
    }
}
