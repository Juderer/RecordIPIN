package com.zhushuli.recordipin.utils;

import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;

import com.zhushuli.recordipin.BaseApplication;
import com.zhushuli.recordipin.MainActivity;

public class PermissionUtils {

    public static final int MY_PERMISSION_REQUEST_CODE = 2024;

    public static boolean checkPermissionGranted(String permission) {
        if (ActivityCompat.checkSelfPermission(BaseApplication.getContext(), permission) ==
                PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return false;
    }

    public static boolean checkPermissionsAllGranted(String[] permissions) {
        for (String permission : permissions) {
            if (!checkPermissionGranted(permission)) {
                return false;
            }
        }
        return true;
    }

    public static boolean shouldShowPermissionRationale(String permission) {
        return false;
    }

    public static void batchRequestPermissions(Activity activity, String[] permissions) {
        ActivityCompat.requestPermissions(activity, permissions, MY_PERMISSION_REQUEST_CODE);
    }
}
