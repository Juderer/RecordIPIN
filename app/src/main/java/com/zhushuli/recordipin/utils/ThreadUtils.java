package com.zhushuli.recordipin.utils;

import android.os.HandlerThread;

public class ThreadUtils {
    public static void sleep(int sleepTimeMillis) {
        try {
            Thread.sleep(sleepTimeMillis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static long threadID() {
        return Thread.currentThread().getId();
    }

    public static void interrupt(Thread thread) {
        if (thread instanceof HandlerThread) {
            HandlerThread handlerThread = (HandlerThread) thread;
            handlerThread.quitSafely();
        } else {
            if (thread.isAlive()) {
                thread.interrupt();
            }
        }
    }
}
