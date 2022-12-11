package com.zhushuli.recordipin.model;

import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;

public class RecordNeighborLte extends RecordNeighbor{

    public RecordNeighborLte() {
        super();
    }

    public RecordNeighborLte(int earfcn, int pci, int rsrp, int rsrq) {
        super(earfcn, pci, rsrp, rsrq);
    }

    public RecordNeighborLte(CellInfo cell) {
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
    }

    @Override
    public void setServiceCell(RecordService serviceCell) {
        return ;
    }
}
