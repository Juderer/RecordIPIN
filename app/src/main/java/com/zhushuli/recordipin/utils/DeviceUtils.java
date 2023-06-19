package com.zhushuli.recordipin.utils;

public class DeviceUtils {

    /**
     * 判断是否为鸿蒙系统
     */
    public static boolean isHarmonyOs() {
        try {
            Class<?> buildExClass = Class.forName("com.huawei.system.BuildEx");
            Object osBrand = buildExClass.getMethod("getOsBrand").invoke(buildExClass);
            if (osBrand == null) {
                return false;
            }
            return "harmony".equalsIgnoreCase(osBrand.toString());
        } catch (Throwable e) {
            return false;
        }
    }

    public static String getPhoneModel() {
        return android.os.Build.MODEL;
    }

    public static String getPhoneBrand() {
        return android.os.Build.BRAND;
    }
}
