package com.zhushuli.recordipin;

import android.app.Application;
import android.content.Context;
import android.util.Log;

/**
 * @author      : zhushuli
 * @createDate  : 2024/06/25 16:04
 * @description : Base application to provide a global context.
 */
public class BaseApplication extends Application {
    private static final String TAG = "BaseApplication";

    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        context = getApplicationContext();
    }

    public static Context getContext() {
        return context;
    }
}
