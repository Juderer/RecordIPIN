package com.zhushuli.recordipin.utils;

public class TimeReferenceUtils {

    private static long elapsedTimeReference;  // 纳秒级
    private static long myTimeReference;  // 毫秒级

    static {
        elapsedTimeReference = 0L;
        myTimeReference = 0L;
    }

    // 目前仅GNSS与IMU采集时使用
    public static synchronized void setTimeReference(long _elapsedTimeReference) {
        if (elapsedTimeReference == 0L && myTimeReference == 0L) {
            elapsedTimeReference = _elapsedTimeReference;
            myTimeReference = System.currentTimeMillis();
        }
    }

    public static long getElapsedTimeReference() {
        return elapsedTimeReference;
    }

    public static long getMyTimeReference() {
        return myTimeReference;
    }
}
