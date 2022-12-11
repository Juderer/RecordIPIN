package com.zhushuli.recordipin.model;

public abstract class RecordNeighbor {
    private int earfcn;
    private int pci;
    private int rsrp;
    private int rsrq;
    private RecordService serviceCell = null;

    public RecordNeighbor() {

    }

    public RecordNeighbor(int earfcn, int pci, int rsrp, int rsrq) {
        this.earfcn = earfcn;
        this.pci = pci;
        this.rsrp = rsrp;
        this.rsrq = rsrq;
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

    public RecordService getServiceCell() {
        return this.serviceCell;
    }

    public abstract void setServiceCell(RecordService serviceCell);
}
