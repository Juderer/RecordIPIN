package com.zhushuli.recordipin.models.cellular;

@Deprecated
public abstract class CellNeighbor extends CellPacket {

    public int earfcn;
    public int pci;
    public int rsrp;
    public int rsrq;
    public CellService serviceCell = null;

    public CellNeighbor() {

    }

    public CellNeighbor(int earfcn, int pci, int rsrp, int rsrq) {
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

    public CellService getServiceCell() {
        return this.serviceCell;
    }

    public abstract void setServiceCell(CellService serviceCell);

    @Override
    public String toString() {
        return getTimeStampMillis() + "," +
                "--" + "," + "--" + "," + "--" + "," + "--" + "," +
                earfcn + "," + pci + "," +
                rsrp + "," + rsrq;
    }
}
