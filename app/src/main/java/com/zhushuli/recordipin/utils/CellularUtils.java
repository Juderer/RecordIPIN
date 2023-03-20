package com.zhushuli.recordipin.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.TelephonyManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CellularUtils {
    public static final int NETWORK_NONE = 0;  // 没有网络连接
    public static final int NETWORK_2G = 2;  // 2G
    public static final int NETWORK_3G = 3;  // 3G
    public static final int NETWORK_4G = 4;  // 4G
    public static final int NETWORK_5G = 5;  // 5G
    public static final int NETWORK_MOBILE = 6;  // 手机流量

    public static boolean hasSimCard(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
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

    public static String getOperatorName(Context context) {
        /**
         * getSimOperatorName()可以直接获得运营商名称
         * getSimOperator()需要根据返回值判断，如"46000"为"中国移动"
         */
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.getSimOperatorName();
    }

    public static String getOperatorEnglishName(Context context) {
        final Map<String, String> operatorEnglishNames = new HashMap<String, String>() {{
            put("中国移动", "China Mobile");
            put("中国联通", "China Unicom");
            put("中国电信", "China Telecom");
        }};
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return operatorEnglishNames.get(telephonyManager.getSimOperatorName());
    }

    public static String getMoblieCountryCode(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.getSimOperator().substring(0, 3);
    }

    public static String getMobileNetworkCode(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.getSimOperator().substring(3, 5);
    }

    public static String getMobileNetworkTypeName(Context context) {
        switch (getMobileNetworkType(context)) {
            case NETWORK_NONE:
                return "Unknown";
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

    private static int getMobileNetworkType(Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (null == connManager) {
            return NETWORK_NONE;  // 认为无网络
        }

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

    public static String transCellInfo2Str(List<CellInfo> cellInfo) {
        List<String> strings = new ArrayList<>();
        for (CellInfo cell : cellInfo) {
            StringBuffer sb = new StringBuffer();
            if (cell instanceof CellInfoLte) {
                // 基站类型：LTE/NR
                sb.append("LTE,");
                CellInfoLte cellInfoLte = (CellInfoLte) cell;
                // 是否是主基站
                if (cellInfoLte.isRegistered()) {
                    sb.append("1,");
                } else {
                    sb.append("0,");
                }
                // 系统时间戳
                sb.append(String.format("%d,", System.currentTimeMillis()));
                // 基于系统开机测算的时间戳？
                long elapsedTime = 0L;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    TimeReferenceUtils.setTimeReference(cellInfoLte.getTimestampMillis() * 1_000_000);
                    elapsedTime = Math.round(TimeReferenceUtils.getMyTimeReference() +
                            (cellInfoLte.getTimestampMillis() * 1_000_000 - TimeReferenceUtils.getElapsedTimeReference()) / 1_000_000.0);
                } else {
                    TimeReferenceUtils.setTimeReference(cellInfoLte.getTimeStamp());
                    elapsedTime = Math.round(TimeReferenceUtils.getMyTimeReference() +
                            (cellInfoLte.getTimeStamp() - TimeReferenceUtils.getElapsedTimeReference()) / 1_000_000.0);
                }
                sb.append(String.format("%d,", elapsedTime));

                CellIdentityLte identityLte = cellInfoLte.getCellIdentity();
                // Mobile Country Code
                sb.append(identityLte.getMccString() + ",");
                // Mobile Network Code
                sb.append(identityLte.getMncString() + ",");
                // Cell Identity
                sb.append(identityLte.getCi() + ",");
                // Tracking Area Code
                sb.append(identityLte.getTac() + ",");
                // Absolute RF Channel Number
                sb.append(identityLte.getEarfcn() + ",");
                // Physical Cell Id
                sb.append(identityLte.getPci() + ",");

                CellSignalStrengthLte signalStrengthLte = cellInfoLte.getCellSignalStrength();
                // Reference Signal Receiving Power
                sb.append(signalStrengthLte.getRsrp() + ",");
                // Reference Signal Receiving Quality
                sb.append(signalStrengthLte.getRsrq() + "\n");
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (cell instanceof CellInfoNr) {
                    // 基站类型：LTE/NR
                    sb.append("NR,");
                    CellInfoNr cellInfoNr = (CellInfoNr) cell;
                    // 是否是主基站
                    if (cellInfoNr.isRegistered()) {
                        sb.append("1,");
                    } else {
                        sb.append("0,");
                    }
                    // 系统时间戳
                    sb.append(String.format("%d,", System.currentTimeMillis()));
                    // 基于系统开机测算的时间戳？
                    long elapsedTime = 0L;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        TimeReferenceUtils.setTimeReference(cellInfoNr.getTimestampMillis() * 1_000_000);
                        elapsedTime = Math.round(TimeReferenceUtils.getMyTimeReference() +
                                (cellInfoNr.getTimestampMillis() * 1_000_000 - TimeReferenceUtils.getElapsedTimeReference()) / 1_000_000.0);
                    } else {
                        TimeReferenceUtils.setTimeReference(cellInfoNr.getTimeStamp());
                        elapsedTime = Math.round(TimeReferenceUtils.getMyTimeReference() +
                                (cellInfoNr.getTimeStamp() - TimeReferenceUtils.getElapsedTimeReference()) / 1_000_000.0);
                    }
                    sb.append(String.format("%d,", elapsedTime));

                    CellIdentityNr identityNr = (CellIdentityNr) cellInfoNr.getCellIdentity();
                    // Mobile Country Code
                    sb.append(identityNr.getMccString() + ",");
                    // Mobile Network Code
                    sb.append(identityNr.getMncString() + ",");
                    // NR(New Radio 5G) Cell Identity
                    sb.append(identityNr.getNci() + ",");
                    // Tracking Area Code
                    sb.append(identityNr.getTac() + ",");
                    // New Radio Absolute Radio Frequency Channel Number
                    sb.append(identityNr.getNrarfcn() + ",");
                    // Physical Cell Id
                    sb.append(identityNr.getPci() + ",");

                    CellSignalStrengthNr signalStrengthNr = (CellSignalStrengthNr) cellInfoNr.getCellSignalStrength();
                    // SS Reference Signal Receiving Quality
                    sb.append(signalStrengthNr.getSsRsrp() + ",");
                    // SS Reference Signal Signal-to-Noise Ratio
                    sb.append(signalStrengthNr.getSsRsrq() + "\n");
                }
            }
            strings.add(sb.toString());
        }
        return String.join("", strings);
    }
}