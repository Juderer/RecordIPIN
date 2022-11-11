package com.zhushuli.recordipin.utils;

public class TimeReferenceUtils {

    private static long elapsedTimeReference;
    private static long myTimeReference;

    static {
        elapsedTimeReference = 0L;
        myTimeReference = 0L;
    }

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
