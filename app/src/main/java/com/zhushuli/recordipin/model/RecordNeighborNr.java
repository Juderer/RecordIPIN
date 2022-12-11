package com.zhushuli.recordipin.model;

import android.os.Build;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoNr;
import android.telephony.CellSignalStrengthNr;

import androidx.annotation.RequiresApi;

public class RecordNeighborNr extends RecordNeighbor{

    public RecordNeighborNr() {
        super();
    }

    public RecordNeighborNr(int earfcn, int pci, int rsrp, int rsrq) {
        super(earfcn, pci, rsrp, rsrq);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public RecordNeighborNr(CellInfo cell) {
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
    }

    @Override
    public void setServiceCell(RecordService serviceCell) {
        return ;
    }
}
