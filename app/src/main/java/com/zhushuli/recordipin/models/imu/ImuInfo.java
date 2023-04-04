package com.zhushuli.recordipin.models.imu;

import android.hardware.Sensor;
import android.hardware.SensorEvent;

import androidx.annotation.NonNull;

/**
 * 属性需要设置为public，以满足fastjson序列化要求！
 * @author      : zhushuli
 * @createDate  : 2023/02/27 11:57
 * @description : 封装需要的IMU信息，目前只用于页面展示
 */
public class ImuInfo {
    // 传感器类型：加速度计或陀螺仪
    public int type;

    public long timestamp;

    public long timeMillis;

    // 传感器三轴示数
    public float[] values;

    public int accuracy;

    public ImuInfo(int type) {
        this.type = type;
    }

    public ImuInfo(int type, float[] values) {
        this.type = type;
        this.values = values;
    }

    public ImuInfo(SensorEvent event) {
        this.type = event.sensor.getType();
        this.timestamp = event.timestamp;
        this.timeMillis = event.timestamp / 1_000_000L;
        this.values = event.values;
        this.accuracy = event.accuracy;
    }

    public ImuInfo() {}

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTimeMillis() {
        return timeMillis;
    }

    public void setTimeMillis(long timeMillis) {
        this.timeMillis = timeMillis;
    }

    public float[] getValues() {
        return values;
    }

    public void setValues(float[] values) {
        this.values = values;
    }

    public int getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(int accuracy) {
        this.accuracy = accuracy;
    }

    @NonNull
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();

        switch (this.type) {
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

        sb.append(this.timeMillis);
        sb.append(",");

        for (float value : values) {
            sb.append(String.format("%.4f", value));
            sb.append(",");
        }

        sb.append(accuracy);
        sb.append("\n");

        return sb.toString();
    }
}
