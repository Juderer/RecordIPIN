package com.zhushuli.recordipin.utils;

import android.hardware.Sensor;
import android.hardware.SensorEvent;

import com.zhushuli.recordipin.models.imu.ImuInfo;

import java.util.Map;


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

//    public static String genImuCsv(SensorEvent event) {
////        calibrateSensorTime(event);
//        String typeStr = "";
//        switch (event.sensor.getType()) {
//            case Sensor.TYPE_ACCELEROMETER:
//            case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
//                typeStr = "ACCEL";
//                break;
//            case Sensor.TYPE_GYROSCOPE:
//            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
//                typeStr = "GYRO";
//                break;
//            default:
//                typeStr = "unknown";
//                break;
//        }
//        String valueStr = "";
//        for (float value: event.values) {
//            valueStr += String.format("%.4f,", value);
//        }
//        return typeStr + "," +
//                String.format("%d,%d,", System.currentTimeMillis(), event.timestamp / 1_000_000L) +
//                valueStr +
//                String.valueOf(event.accuracy) + "\n";
//    }

    public static String genImuCsv(SensorEvent event) {
        StringBuffer sb = new StringBuffer();
        // 传感器类型
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
            case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
                sb.append("ACCEL");
                break;
            case Sensor.TYPE_GYROSCOPE:
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                sb.append("GYRO");
                break;
            default:
                sb.append("unknown");
                break;
        }
        sb.append(",");

        // 时间戳
        sb.append(System.currentTimeMillis());
        sb.append(",");
        sb.append(event.timestamp / 1_000_000L);
        sb.append(",");

        // 传感器数值
        for (float value: event.values) {
            sb.append(String.format("%.4f", value));
            sb.append(",");
        }
        sb.append(event.accuracy);
        sb.append("\n");

        return sb.toString();
    }
}
