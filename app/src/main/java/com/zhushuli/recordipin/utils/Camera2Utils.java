package com.zhushuli.recordipin.utils;

import android.hardware.camera2.CameraCharacteristics;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author : zhushuli
 * @createDate : 2023/04/13 01:40
 * @description : Camera2相关工具库
 */
public class Camera2Utils {

    private static final double ASPECT_RATIO_TOLERANCE = 0.005D;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    public static int sensorToDeviceRotation(CameraCharacteristics c, int deviceOrientation) {
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);

        // Get device orientation in degrees
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);

        // Reverse device orientation for front-facing cameras
        if (c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
            deviceOrientation = -deviceOrientation;
        }

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        return (sensorOrientation - deviceOrientation + 360) % 360;
    }

    public static int sensorToDeviceRotationByListener(CameraCharacteristics c, int orientation) {
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);

        if (c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
            orientation = -orientation;
        }

        return (sensorOrientation - orientation + 360) % 360;
    }

    public static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    public static boolean checkAspectsEqual(Size a, Size b) {
        double aAspect = a.getWidth() / (double) a.getHeight();
        double bAspect = b.getWidth() / (double) b.getHeight();
        return Math.abs(aAspect - bAspect) <= ASPECT_RATIO_TOLERANCE;
    }

    public static Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight, Size aspectRatio) {
        List<Size> bigEnough = new ArrayList<>();
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() > textureViewWidth && option.getHeight() > textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e("Camera2Utils", "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    public static boolean contains(int[] modes, int mode) {
        if (modes == null) {
            return false;
        }
        for (int i : modes) {
            if (i == mode) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断正在使用的设备是否只支持LEGACY硬件级别
     * @param characteristics 相机属性
     * @return 如果是延时设备返回true
     */
    public static boolean isLegacyLocked(CameraCharacteristics characteristics) {
        return characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ==
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
    }

    public static long getTimerLocked() {
        return SystemClock.elapsedRealtime();  // milliseconds
    }

    public static int discretizeOrientation(int orientation) {
        if (orientation > 45 && orientation <= 135) {
            return 90;
        } else if (orientation > 135 && orientation <= 225) {
            return 180;
        } else if (orientation > 225 && orientation < 315) {
            return 270;
        } else {
            return 0;
        }
    }
}