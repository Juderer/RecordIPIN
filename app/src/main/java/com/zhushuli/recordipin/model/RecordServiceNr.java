package com.zhushuli.recordipin.model;

import android.os.Build;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoNr;
import android.telephony.CellSignalStrengthNr;

import androidx.annotation.RequiresApi;

public class RecordServiceNr extends RecordService{
    public RecordServiceNr() {
        super();
    }

    public RecordServiceNr(String mcc, String mnc, int cid, int tac, int earfcn, int pci) {
        super(mcc, mnc, cid, tac, earfcn, pci);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public RecordServiceNr(CellInfo cellInfo) {
        if (cellInfo instanceof CellInfoNr && cellInfo.isRegistered()) {
            CellInfoNr cellInfoNr = (CellInfoNr) cellInfo;
            recordFromCellInfoNr(cellInfoNr);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void recordFromCellInfoNr(CellInfoNr cellInfoNr) {
        CellIdentityNr identityNr = (CellIdentityNr) cellInfoNr.getCellIdentity();
        CellSignalStrengthNr cssNr = (CellSignalStrengthNr) cellInfoNr.getCellSignalStrength();

        setMcc(identityNr.getMccString());
        setMnc(identityNr.getMncString());
        setCid(identityNr.getNci());
        setTac(identityNr.getTac());
        setEarfcn(identityNr.getNrarfcn());
        setPci(identityNr.getPci());

        setRsrp(cssNr.getSsRsrp());
        setRsrq(cssNr.getSsRsrq());
    }
}
