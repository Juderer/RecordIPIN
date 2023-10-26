package com.zhushuli.recordipin.utils.location;

import android.location.GnssStatus;
import android.os.Build;

import com.zhushuli.recordipin.models.location.SatelliteInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author      : zhushuli
 * @createDate  : 2023/10/23 23:36
 * @description : 处理卫星数据
 */
public class SatelliteUtils {

    public static List<SatelliteInfo> genSatelliteInfos(GnssStatus status) {
        List<SatelliteInfo> satelliteInfos = new ArrayList<>();

        int count = status.getSatelliteCount();
        for (int i = 0; i < count; i++) {
            float azimuth = status.getAzimuthDegrees(i);
            float basebandCn0DbHz = -1;
            float carrierFrequencyHz = -1;
            float cn0DbHz = status.getCn0DbHz(i);
            int constellationType = status.getConstellationType(i);
            float elevation = status.getElevationDegrees(i);
            int svid = status.getSvid(i);
            boolean fixed = status.usedInFix(i);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (status.hasBasebandCn0DbHz(i)) {
                    basebandCn0DbHz = status.getBasebandCn0DbHz(i);
                }
            }
            if (status.hasCarrierFrequencyHz(i)) {
                carrierFrequencyHz = status.getCarrierFrequencyHz(i);
            }
            satelliteInfos.add(new SatelliteInfo(azimuth, basebandCn0DbHz,
                    carrierFrequencyHz, cn0DbHz, constellationType, elevation, svid, fixed));
        }

        return satelliteInfos;
    }
}
