package com.zhushuli.recordipin.utils.location;

import android.location.GnssClock;

import java.util.Map;

/**
 * @author      : zhushuli
 * @createDate  : 2023/03/31 10:59
 * @description : 计算UTC时间戳
 */
public class GnssClockUtils {

    public static long calcGpsUtcTimeNanos(GnssClock clock) {
        long timeNanos;
        long fullBiasNanos;
        double biasNanos;
        int leapSecond;

        timeNanos = clock.getTimeNanos();
        if (clock.hasFullBiasNanos()) {
            fullBiasNanos = clock.getFullBiasNanos();
        } else {
            fullBiasNanos = 0L;
        }
        if (clock.hasBiasNanos()) {
            biasNanos = clock.getBiasNanos();
        } else {
            biasNanos = 0.0D;
        }
        if (clock.hasLeapSecond()) {
            leapSecond = clock.getLeapSecond();
        } else {
            leapSecond = 18;
        }

        long gpsUtcTimeNanos = (long) (timeNanos - (fullBiasNanos + biasNanos) - leapSecond * 1_000_000_000L);
        return gpsUtcTimeNanos;
    }

    public static long calcGpsUtcTimeMillis(GnssClock clock) {
        long gpsUtcTimeNanos = calcGpsUtcTimeNanos(clock);
        long gpsUtcTimeMillis = gpsUtcTimeNanos / 1_000_000L;
        return gpsUtcTimeMillis;
    }

    public static long calcUtcTimeNanos(GnssClock clock) {
        // GPS时间戳从1980年1月6日0时开始
        // UTC时间戳从1970年1月1日0时开始
        long gpsUtcTimeNanos = calcGpsUtcTimeNanos(clock);
        long utcTimeNanos = gpsUtcTimeNanos + 315964800_000_000_000L;
        return utcTimeNanos;
    }

    public static long calcUtcTimeMillis(GnssClock clock) {
        long gpsUtcTimeMillis = calcGpsUtcTimeMillis(clock);
        long utcTimeMillis = gpsUtcTimeMillis + 315964800_000L;
        return utcTimeMillis;
    }

    public static String transPair2String(Map.Entry<Long, GnssClock> pair) {
        return String.format("%d,%d\n", pair.getKey(), calcUtcTimeMillis(pair.getValue()));
    }
}