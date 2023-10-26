package com.zhushuli.recordipin.models.location;

import android.location.GnssStatus;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import com.zhushuli.recordipin.R;

/**
 * @author      : zhushuli
 * @createDate  : 2023/10/23 22:38
 * @description : {@link android.location.GnssStatus}需保证API 31及以上才可使用Parcelable
 */
public class SatelliteInfo implements Parcelable {

    private float mAzimuth;

    private float mBasebandCn0DbHz;

    private float mCarrierFrequencyHz;

    private float mCn0DbHz;

    private int mConstellationType;

    private float mElevation;

    private int mSvid;

    private boolean used;

    private int mFlag;

    private int mPngFlag;

    private String mCarrierFrequencyName;

    public SatelliteInfo(float azimuth, float basebandCn0DbHz, float carrierFrequencyHz, float cn0DbHz,
                            int constellationType, float elevation, int svid, boolean used) {
        mAzimuth = azimuth;
        mBasebandCn0DbHz = basebandCn0DbHz;
        mCarrierFrequencyHz = carrierFrequencyHz;
        mCn0DbHz = cn0DbHz;
        mConstellationType = constellationType;
        mElevation = elevation;
        mSvid = svid;
        this.used = used;
        setFlag();
    }

    protected SatelliteInfo(Parcel in) {
        mAzimuth = in.readFloat();
        mBasebandCn0DbHz = in.readFloat();
        mCarrierFrequencyHz = in.readFloat();
        mCn0DbHz = in.readFloat();
        mConstellationType = in.readInt();
        mElevation = in.readFloat();
        mSvid = in.readInt();
        used = in.readByte() == 1;
        setFlag();
    }

    private void setFlag() {
        if (mConstellationType == GnssStatus.CONSTELLATION_GPS) {
            mFlag = R.drawable.us;
            mPngFlag = R.drawable.us_bit;
        }
        else if (mConstellationType == GnssStatus.CONSTELLATION_BEIDOU) {
            mFlag = R.drawable.cn;
            mPngFlag = R.drawable.cn_bit;
        }
        else if (mConstellationType == GnssStatus.CONSTELLATION_GALILEO) {
            mFlag = R.drawable.eu;
            mPngFlag = R.drawable.eu_bit;
        }
        else if (mConstellationType == GnssStatus.CONSTELLATION_GLONASS) {
            mFlag = R.drawable.ru;
            mPngFlag = R.drawable.ru_bit;
        }
        else if (mConstellationType == GnssStatus.CONSTELLATION_QZSS) {
            mFlag = R.drawable.jp;
            mPngFlag = R.drawable.jp_bit;
        }
        else if (mConstellationType == GnssStatus.CONSTELLATION_IRNSS) {
            mFlag = R.drawable.in;
            mPngFlag = R.drawable.in_bit;
        }
        else {
            mFlag = R.drawable.blank;
            mPngFlag = R.drawable.blank_bit;
        }
    }

    public float getAzimuth() {
        return mAzimuth;
    }

    public float getBasebandCn0DbHz() {
        return mBasebandCn0DbHz;
    }

    public float getCarrierFrequencyHz() {
        return mCarrierFrequencyHz;
    }

    public float getCn0DbHz() {
        return mCn0DbHz;
    }

    public int getConstellationType() {
        return mConstellationType;
    }

    public float getElevation() {
        return mElevation;
    }

    public int getSvid() {
        return mSvid;
    }

    public boolean isUsed() {
        return used;
    }

    public int getFlag() {
        return mFlag;
    }

    public int getPngFlag() {
        return mPngFlag;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SatelliteInfo)) {
            return false;
        }
        SatelliteInfo that = (SatelliteInfo) obj;
        return mAzimuth == that.mAzimuth
                && mBasebandCn0DbHz == that.mBasebandCn0DbHz
                && mCarrierFrequencyHz == that.mCarrierFrequencyHz
                && mCn0DbHz == that.mCn0DbHz
                && mConstellationType == that.mConstellationType
                && mElevation == that.mElevation
                && mSvid == that.mSvid
                && used == used;
    }

    public static final Creator<SatelliteInfo> CREATOR = new Creator<SatelliteInfo>() {
        @Override
        public SatelliteInfo createFromParcel(Parcel in) {
            return new SatelliteInfo(in);
        }

        @Override
        public SatelliteInfo[] newArray(int size) {
            return new SatelliteInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(mAzimuth);
        dest.writeFloat(mBasebandCn0DbHz);
        dest.writeFloat(mCarrierFrequencyHz);
        dest.writeFloat(mCn0DbHz);
        dest.writeInt(mConstellationType);
        dest.writeFloat(mElevation);
        dest.writeInt(mSvid);
        dest.writeByte(used ? (byte) 1 : (byte) 0);
    }
}