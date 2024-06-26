package com.zhushuli.recordipin.models.cellular;

@Deprecated
public abstract class CellPacket {

    // Observation time stamped as type in nanoseconds since boot
    // 同CellInfo类中的属性一致
    public long mTimeStamp;

    public CellPacket() {

    }

    public CellPacket(long timeStamp) {
        this.mTimeStamp = timeStamp;
    }

    public long getTimeStampMillis() {
        return this.mTimeStamp;
    }

    public long getTimeStamp() {
        return this.mTimeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.mTimeStamp = timeStamp;
    }
}
