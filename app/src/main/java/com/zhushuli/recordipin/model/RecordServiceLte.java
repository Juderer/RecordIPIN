package com.zhushuli.recordipin.model;

import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;

public class RecordServiceLte extends RecordService {

    public RecordServiceLte() {
        super();
    }

    public RecordServiceLte(String mcc, String mnc, int cid, int tac, int earfcn, int pci) {
        super(mcc, mnc, cid, tac, earfcn, pci);
    }

    public RecordServiceLte(CellInfo cellInfo) {
        if (cellInfo instanceof CellInfoLte && cellInfo.isRegistered()) {
            CellInfoLte cellInfoLte = (CellInfoLte) cellInfo;
            recordFromCellInfoLte(cellInfoLte);
        }
    }

    private void recordFromCellInfoLte(CellInfoLte cellInfoLte) {
        CellIdentityLte identityLte = cellInfoLte.getCellIdentity();
        CellSignalStrengthLte cssLte = cellInfoLte.getCellSignalStrength();

        setMcc(identityLte.getMccString());
        setMnc(identityLte.getMncString());
        setCid(identityLte.getCi());
        setTac(identityLte.getTac());
        setEarfcn(identityLte.getEarfcn());
        setPci(identityLte.getPci());

        setRsrp(cssLte.getRsrp());
        setRsrq(cssLte.getRsrq());
    }
}