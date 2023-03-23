package com.zhushuli.recordipin.models.cellular;

@Deprecated
public abstract class CellService extends CellPacket {

    public String mcc;
    public String mnc;
    public long cid;
    public int tac;
    public int earfcn;
    public int pci;
    public int rsrp;
    public int rsrq;

    public CellService() {

    }

    public CellService(String mcc, String mnc, int cid, int tac, int earfcn, int pci) {
        this.mcc = mcc;
        this.mnc = mnc;
        this.cid = cid;
        this.tac = tac;
        this.earfcn = earfcn;
        this.pci = pci;
    }

    public String getMcc() {
        return mcc;
    }

    public void setMcc(String mcc) {
        this.mcc = mcc;
    }

    public String getMnc() {
        return mnc;
    }

    public void setMnc(String mnc) {
        this.mnc = mnc;
    }

    public long getCid() {
        return cid;
    }

    public void setCid(long cid) {
        this.cid = cid;
    }

    public int getTac() {
        return tac;
    }

    public void setTac(int tac) {
        this.tac = tac;
    }

    public int getEarfcn() {
        return earfcn;
    }

    public void setEarfcn(int earfcn) {
        this.earfcn = earfcn;
    }

    public int getPci() {
        return pci;
    }

    public void setPci(int pci) {
        this.pci = pci;
    }

    public int getRsrp() {
        return rsrp;
    }

    public void setRsrp(int rsrp) {
        this.rsrp = rsrp;
    }

    public int getRsrq() {
        return rsrq;
    }

    public void setRsrq(int rsrq) {
        this.rsrq = rsrq;
    }

    @Override
    public String toString() {
        return getTimeStampMillis() + "," +
                mcc + "," + mnc + "," + cid + "," + tac + "," +
                earfcn + "," + pci + "," +
                rsrp + "," + rsrq;
    }
}
