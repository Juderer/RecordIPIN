package com.zhushuli.recordipin.models.cellular;

import android.os.Build;
import android.os.SystemClock;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoNr;
import android.telephony.CellSignalStrengthNr;

import androidx.annotation.RequiresApi;

@Deprecated
public class CellServiceNr extends CellService {
    public CellServiceNr() {
        super();
    }

    public CellServiceNr(String mcc, String mnc, int cid, int tac, int earfcn, int pci) {
        super(mcc, mnc, cid, tac, earfcn, pci);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public CellServiceNr(CellInfo cellInfo) {
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
}
