package com.zhushuli.recordipin.utils;

public class ThreadUtils {
    public static void threadSleep(int sleepTimeMillis) {
        try {
            Thread.sleep(sleepTimeMillis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static long threadID() {
        return Thread.currentThread().getId();
    }
}
