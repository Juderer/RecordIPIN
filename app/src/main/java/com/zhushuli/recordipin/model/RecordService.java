package com.zhushuli.recordipin.model;

public abstract class RecordService {
    private String mcc;
    private String mnc;
    private long cid;
    private int tac;
    private int earfcn;
    private int pci;
    private int rsrp;
    private int rsrq;

    public RecordService() {

    }

    public RecordService(String mcc, String mnc, int cid, int tac, int earfcn, int pci) {
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
}
