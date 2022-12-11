package com.zhushuli.recordipin.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;

public class NetworkUtils {
    public static final int NETWORK_NONE = 0;  // 没有网络连接
    public static final int NETWORK_WIFI = 1;  // WiFi连接
    public static final int NETWORK_2G = 2;  // 2G
    public static final int NETWORK_3G = 3;  // 3G
    public static final int NETWORK_4G = 4;  // 4G
    public static final int NETWORK_5G = 5;  // 5G
    public static final int NETWORK_MOBILE = 6;  // 手机流量

    public static boolean hasSimCard(TelephonyManager telephonyManager) {
        int simState = telephonyManager.getSimState();
        switch (simState) {
            case TelephonyManager.SIM_STATE_ABSENT:
            case TelephonyManager.SIM_STATE_UNKNOWN:
                return false;
            default:
                break;
        }
        return true;
    }

    public static String getNetworkTypeName(Context context) {
        switch (getNetworkType(context)) {
            case NETWORK_NONE:
                return "Unknown";
            case NETWORK_WIFI:
                return "WiFi";
            case NETWORK_MOBILE:
                return "Cellular";
            case NETWORK_2G:
                return "2G";
            case NETWORK_3G:
                return "3G";
            case NETWORK_4G:
                return "LTE";
            case NETWORK_5G:
                return "NR";
            default:
                return "Unknown";
        }
    }

    @SuppressLint("MissingPermission")
    private static int getNetworkType(Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (null == connManager) {
            return NETWORK_NONE;  // 认为无网络
        }
        Network network = connManager.getActiveNetwork();
        NetworkCapabilities networkCapabilities = connManager.getNetworkCapabilities(network);

        // 判断是否为WiFi
        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return NETWORK_WIFI;
        }

//        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
//            Log.d(TAG, "Cellular");
//            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
//            int networkType = telephonyManager.getDataNetworkType();
//            Log.d("CellularActivity", "network type " + networkType);
//            switch (networkType) {
//                /*
//                GPRS : 2G(2.5) General Packet Radia Service 114kbps
//                EDGE : 2G(2.75G) Enhanced Data Rate for GSM Evolution 384kbps
//                UMTS : 3G WCDMA 联通3G Universal Mobile Telecommunication System 完整的3G移动通信技术标准
//                CDMA : 2G 电信 Code Division Multiple Access 码分多址
//                EVDO_0 : 3G (EVDO 全程 CDMA2000 1xEV-DO) Evolution - Data Only (Data Optimized) 153.6kps - 2.4mbps 属于3G
//                EVDO_A : 3G 1.8mbps - 3.1mbps 属于3G过渡，3.5G
//                1xRTT : 2G CDMA2000 1xRTT (RTT - 无线电传输技术) 144kbps 2G的过渡,
//                HSDPA : 3.5G 高速下行分组接入 3.5G WCDMA High Speed Downlink Packet Access 14.4mbps
//                HSUPA : 3.5G High Speed Uplink Packet Access 高速上行链路分组接入 1.4 - 5.8 mbps
//                HSPA : 3G (分HSDPA,HSUPA) High Speed Packet Access
//                IDEN : 2G Integrated Dispatch Enhanced Networks 集成数字增强型网络 （属于2G，来自维基百科）
//                EVDO_B : 3G EV-DO Rev.B 14.7Mbps 下行 3.5G
//                LTE : 4G Long Term Evolution FDD-LTE 和 TDD-LTE , 3G过渡，升级版 LTE Advanced 才是4G
//                EHRPD : 3G CDMA2000向LTE 4G的中间产物 Evolved High Rate Packet Data HRPD的升级
//                HSPAP : 3G HSPAP 比 HSDPA 快些
//                 */
//                // 2G网络
//                case TelephonyManager.NETWORK_TYPE_GPRS:
//                case TelephonyManager.NETWORK_TYPE_CDMA:
//                case TelephonyManager.NETWORK_TYPE_EDGE:
//                case TelephonyManager.NETWORK_TYPE_1xRTT:
//                case TelephonyManager.NETWORK_TYPE_IDEN:
//                    return NETWORK_2G;
//                case TelephonyManager.NETWORK_TYPE_EVDO_A:
//                case TelephonyManager.NETWORK_TYPE_UMTS:
//                case TelephonyManager.NETWORK_TYPE_EVDO_0:
//                case TelephonyManager.NETWORK_TYPE_HSDPA:
//                case TelephonyManager.NETWORK_TYPE_HSUPA:
//                case TelephonyManager.NETWORK_TYPE_EVDO_B:
//                case TelephonyManager.NETWORK_TYPE_EHRPD:
//                case TelephonyManager.NETWORK_TYPE_HSPAP:
//                    return NETWORK_3G;
//                case TelephonyManager.NETWORK_TYPE_LTE:
//                    return NETWORK_4G;
//                case TelephonyManager.NETWORK_TYPE_NR:
//                    return NETWORK_5G;
//                default:
//                    return NETWORK_MOBILE;
//            }
//        }

        // 获取网络类型，如果为空，返回无网络
        NetworkInfo activeNetInfo = connManager.getActiveNetworkInfo();
        if (activeNetInfo == null || !activeNetInfo.isConnected()) {
            return NETWORK_NONE;
        }
        int networkType = activeNetInfo.getSubtype();
        switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
                return NETWORK_2G;
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return NETWORK_3G;
            case TelephonyManager.NETWORK_TYPE_LTE:
                return NETWORK_4G;
            case TelephonyManager.NETWORK_TYPE_NR:
                return NETWORK_5G;
            default:
                return NETWORK_MOBILE;
        }
    }
}
