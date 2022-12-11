package com.zhushuli.recordipin.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.TelephonyManager;

public class CellularUtils {
    public static final int NETWORK_NONE = 0;  // 没有网络连接
    public static final int NETWORK_2G = 2;  // 2G
    public static final int NETWORK_3G = 3;  // 3G
    public static final int NETWORK_4G = 4;  // 4G
    public static final int NETWORK_5G = 5;  // 5G
    public static final int NETWORK_MOBILE = 6;  // 手机流量

    public static String printCellularInfo(CellInfo cell) {
        StringBuffer sb = new StringBuffer();
        if (cell instanceof CellInfoLte) {
            CellInfoLte cellLte = (CellInfoLte) cell;
            CellSignalStrengthLte signalStrengthLte = cellLte.getCellSignalStrength();
            CellIdentityLte identityLte = cellLte.getCellIdentity();

            sb.append(signalStrengthLte.getRsrp());
            sb.append(",");
            sb.append(signalStrengthLte.getRsrq());
            sb.append(",");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                sb.append(signalStrengthLte.getRssi());
            }
            sb.append(";");

            // 判断是否为主基站
            if (cellLte.isRegistered()) {
                sb.append(identityLte.getCi());
                sb.append(",");
                sb.append(identityLte.getTac());
                sb.append(",");
            }
            sb.append(identityLte.getEarfcn());
            sb.append(",");
            sb.append(identityLte.getPci());
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (cell instanceof CellInfoNr) {
                CellInfoNr cellNr = (CellInfoNr) cell;
                CellSignalStrengthNr signalStrengthNr = (CellSignalStrengthNr) cellNr.getCellSignalStrength();
                CellIdentityNr identityNr = (CellIdentityNr) cellNr.getCellIdentity();

                sb.append(signalStrengthNr.getSsRsrp());
                sb.append(",");
                sb.append(signalStrengthNr.getSsRsrq());
                sb.append(",");
                sb.append(signalStrengthNr.getSsSinr());
                sb.append(";");

                // 判断是否为主基站
                if (cellNr.isRegistered()) {
                    sb.append(identityNr.getNci());
                    sb.append(",");
                    sb.append(identityNr.getTac());
                    sb.append(",");
                }
                sb.append(identityNr.getNrarfcn());
                sb.append(",");
                sb.append(identityNr.getPci());
            }
        } else if (cell instanceof CellInfoGsm) {
            CellInfoGsm cellGsm = (CellInfoGsm) cell;
            CellSignalStrengthGsm signalStrengthGsm = cellGsm.getCellSignalStrength();
            CellIdentityGsm identityGsm = cellGsm.getCellIdentity();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                sb.append(signalStrengthGsm.getRssi());
                sb.append("\t");
            }
            sb.append(identityGsm.getLac());
            sb.append(",");
            sb.append(identityGsm.getArfcn());
            sb.append(",");
            sb.append(identityGsm.getMccString());
            sb.append(",");
            sb.append(identityGsm.getMncString());
        }
        return sb.toString();
    }

    public static String getOperatorName(TelephonyManager telephonyManager) {
        /**
         * getSimOperatorName()可以直接获得运营商名称
         * getSimOperator()需要根据返回值判断，如"46000"为"中国移动"
         */
        return telephonyManager.getSimOperatorName();
    }

    public static String getMoblieCountryCode(TelephonyManager telephonyManager) {
        return telephonyManager.getSimOperator().substring(0, 3);
    }

    public static String getMobileNetworkCode(TelephonyManager telephonyManager) {
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
}
