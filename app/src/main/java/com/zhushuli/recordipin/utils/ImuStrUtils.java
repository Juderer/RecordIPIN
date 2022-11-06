package com.zhushuli.recordipin.utils;

import android.hardware.Sensor;
import android.hardware.SensorEvent;


public class ImuStrUtils {

    public static String sensorEvent2Str(SensorEvent event) {
        String typeStr = "";
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                typeStr = "ACCE";
                break;
            case Sensor.TYPE_GYROSCOPE:
                typeStr = "GYRO";
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                typeStr = "MAGN";
                break;
            default:
                typeStr = "unknown";
                break;
        }
        String valueStr = "";
        for (float value: event.values) {
            valueStr += String.format("%.4f,", value);
        }
        return typeStr + "," +
                String.format("%d,%d,", System.currentTimeMillis(), event.timestamp) +
                valueStr +
                String.valueOf(event.accuracy) + "\n";
    }
}
