package com.zhushuli.recordipin.utils;

import android.hardware.Sensor;
import android.hardware.SensorEvent;


public class ImuUtils {

//    private static long sensorTimeReference;
//    private static long myTimeReference;
//
//    static {
//        // 解决SensorEvent时间戳问题
//        sensorTimeReference = 0L;
//        myTimeReference = 0L;
//    }

//    private static void calibrateSensorTime(SensorEvent event) {
//        // set reference time
//        if (sensorTimeReference == 0L && myTimeReference == 0L) {
//            sensorTimeReference = event.timestamp;
//            myTimeReference = System.currentTimeMillis();
//        }
//        // set event timestamp to current time in milliseconds
//        event.timestamp = myTimeReference +
//                Math.round((event.timestamp - sensorTimeReference) / 1000000L);
//    }

    private static void calibrateSensorTime(SensorEvent event) {
        // set reference time
        TimeReferenceUtils.setTimeReference(event.timestamp);
        // set event timestamp to current time in milliseconds
        event.timestamp = TimeReferenceUtils.getMyTimeReference() +
                Math.round((event.timestamp - TimeReferenceUtils.getElapsedTimeReference()) / 1000000L);
    }

    public static String sensorEvent2Str(SensorEvent event) {
        calibrateSensorTime(event);
        String typeStr = "";
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                typeStr = "ACCEL";
                break;
            case Sensor.TYPE_GYROSCOPE:
                typeStr = "GYRO";
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
