package com.zhushuli.recordipin.utils;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author      : zhushuli
 * @createDate  : 2024/06/18 17:13
 * @description : WiFi tools.
 */
public class WiFiUtils {
    public static int getChannelWidth(int channelWidth) {
        switch (channelWidth) {
            case ScanResult.CHANNEL_WIDTH_20MHZ:
                return 20;
            case ScanResult.CHANNEL_WIDTH_40MHZ:
                return 40;
            case ScanResult.CHANNEL_WIDTH_80MHZ:
                return 80;
            case ScanResult.CHANNEL_WIDTH_160MHZ:
            case ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ:
                return 160;
            default:
                return -1;
        }
    }

    private static String transScanResult2Str(ScanResult scanResult) {
        long sysClockTimeNanos = SystemClock.elapsedRealtimeNanos();
        long sysTimeMills = System.currentTimeMillis();
//        long sysTimeNanos = System

        StringBuilder sb = new StringBuilder();
        sb.append(sysClockTimeNanos).append(",");
        sb.append(sysTimeMills).append(",");
        sb.append(scanResult.timestamp).append(",");

        // Basic Service Set Identifier
        sb.append(scanResult.BSSID).append(",");
        // Service Set Identifier
        sb.append(scanResult.SSID).append(",");
        // Detected signal level in dBm, also known as the RSSI
        sb.append(scanResult.level).append(",");
        // Access point channel bandwidth
        sb.append(getChannelWidth(scanResult.channelWidth)).append("MHZ").append("\n");
        return sb.toString();
    }

    public static String transScanResults2Str(List<ScanResult> scanResults) {
        List<String> strings = new ArrayList<>();
        for (ScanResult scanResult : scanResults) {
            strings.add(transScanResult2Str(scanResult));
        }
        return String.join("", strings);
    }

    public static boolean isWiFiEnabled(Context context) {
        final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
//            final Intent wifiSettingIntent = new Intent(Settings.ACTION_WIFI_SETTINGS);
//            wifiSettingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            startActivity(wifiSettingIntent);
            if (Build.VERSION.SDK_INT >= 29) {
                final Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
                panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(panelIntent);
            } else {
                Toast.makeText(context, "Please turn on WiFi", Toast.LENGTH_SHORT).show();
                new Handler().postDelayed(() -> {
                    try {
                        final Intent wifiSettingIntent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                        wifiSettingIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(wifiSettingIntent);
                    } catch (Exception e) {
                        // An IllegalStateException can occur when the fragment is no longer attached to the activity
                        Log.e("WiFiUtils", "Could not kick off the Wifi Settings Intent for the older pre Android 10 setup");
                    }
                }, 2_000);
            }
            return false;
        }
        return true;
    }

    public static class WiFiScanResultComparator implements Comparator<ScanResult> {
        @Override
        public int compare(ScanResult o1, ScanResult o2) {
            return o2.level - o1.level;
        }
    }
}
