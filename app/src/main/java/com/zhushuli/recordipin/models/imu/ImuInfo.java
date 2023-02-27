package com.zhushuli.recordipin.models.imu;

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
    // 传感器三轴示数
    public float[] values;

    public ImuInfo(int type) {
        this.type = type;
    }

    public ImuInfo(int type, float[] values) {
        this.type = type;
        this.values = values;
    }

    public ImuInfo(SensorEvent event) {
        this.type = event.sensor.getType();
        this.values = event.values;
    }

    public ImuInfo() {}

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public float[] getValues() {
        return values;
    }

    public void setValues(float[] values) {
        this.values = values;
    }

    @NonNull
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(getType());
        for (float value : values) {
            sb.append(',');
            sb.append(String.format("%.4f", value));
        }
        return sb.toString();
    }
}
