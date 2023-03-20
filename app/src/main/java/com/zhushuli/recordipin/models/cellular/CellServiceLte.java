package com.zhushuli.recordipin.models.cellular;

import android.os.Build;
import android.os.SystemClock;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;

public class CellServiceLte extends CellService {
    public int ta;

    public CellServiceLte() {

    }

    public CellServiceLte(String mcc, String mnc, int cid, int tac, int earfcn, int pci) {
        super(mcc, mnc, cid, tac, earfcn, pci);
    }

    public CellServiceLte(CellInfo cellInfo) {
        if (cellInfo instanceof CellInfoLte && cellInfo.isRegistered()) {
            CellInfoLte cellInfoLte = (CellInfoLte) cellInfo;
            recordFromCellInfoLte(cellInfoLte);
        }
    }

    private void recordFromCellInfoLte(CellInfoLte cellInfoLte) {
        CellIdentityLte identityLte = cellInfoLte.getCellIdentity();
        CellSignalStrengthLte cssLte = cellInfoLte.getCellSignalStrength();

        setTa(cssLte.getTimingAdvance());
        setMcc(identityLte.getMccString());
        setMnc(identityLte.getMncString());
        setCid(identityLte.getCi());
        setTac(identityLte.getTac());
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

    public int getTa() {
        return ta;
    }

    public void setTa(int ta) {
        this.ta = ta;
        if (ta == CellInfo.UNAVAILABLE) {
            this.ta = -1;
        }
    }
}