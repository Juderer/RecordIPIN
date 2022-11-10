package com.zhushuli.recordipin.utils;

import android.location.Location;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class LocationUtils {
    /**
     * 方法参数均包括位置信息
     * 方法返回值均为字符串*/

    private static DecimalFormat dfLon;
    private static DecimalFormat dfSpd;
    private static SimpleDateFormat formatter;

    static {
        dfLon = new DecimalFormat("#.000000");  // (经纬度)保留小数点后六位
        dfSpd = new DecimalFormat("#0.00");  // (速度或航向)保留小数点后两位
        formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    public static String genLocationCsv(Location location) {
        // system timestamp, GNSS timestamp, longitude, latitude, accuracy, speed, speed accuracy, bearing, bearing accuracy
        String csvString = String.format("%d,%d,%.6f,%.6f,%.2f,%.2f,%.2f,%.2f,%.2f\n",
                System.currentTimeMillis(), location.getTime(),
                location.getLongitude(), location.getLatitude(), location.getAccuracy(),
                location.getSpeed(), location.getSpeedAccuracyMetersPerSecond(),
                location.getBearing(), location.getBearingAccuracyDegrees());
        return csvString;
    }

    public static Map genLocationMap(Location location) {
        HashMap<String, String> map = new HashMap();
        map.put("date", formatter.format(new Date(System.currentTimeMillis())));
        map.put("time", String.valueOf(location.getTime()));
        map.put("location", String.format("%.6f,%.6f", location.getLongitude(), location.getLatitude()));
        map.put("accuracy", String.format("%.2f", location.getAccuracy()));
        map.put("speed", String.format("%.2fm/s,%.2fkm/h", location.getSpeed(), location.getSpeed() * 3.6));
        map.put("bearing", String.format("%.2f", location.getBearing()));
        map.put("altitude", String.format("%.2fm", location.getAltitude()));
        return map;
    }

    public static String printLocationMsg(Location location) {
        StringBuilder sb = new StringBuilder();
        sb.append("SysTime:\t");
        sb.append(formatter.format(new Date(System.currentTimeMillis())));
        sb.append("\nTime:\t");
        sb.append(location.getTime());
        sb.append("\nLongitude:\t");
        sb.append(dfLon.format(location.getLongitude()));
        sb.append("\nLatitude:\t");
        sb.append(dfLon.format(location.getLatitude()));
        sb.append("\nAccuracy:\t");
        sb.append((int) location.getAccuracy());
        sb.append("\nSpeed:\t");
        sb.append(dfSpd.format(location.getSpeed()));
        sb.append("\nBearing:\t");
        sb.append(dfSpd.format(location.getBearing()));
        sb.append("\n");
        return sb.toString();
    }
}
