package com.zhushuli.recordipin.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.zhushuli.recordipin.MainActivity;
import com.zhushuli.recordipin.R;

/**
 * @author      : zhushuli
 * @createDate  : 2023/04/04 11:00
 * @description : Notification工具类，目前为前台服务提供支持
 */
public class NotificationUtils {

    public static Notification createNotification(Context context, String channelID, String channelName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(context, channelID, channelName);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("RecordIPIN")
                    .setContentText(channelName)
                    .setPriority(NotificationCompat.PRIORITY_HIGH);
            Notification notification = builder.build();
            return notification;
        }
        return null;
    }

    private static void createNotificationChannel(Context context, String channelID, String channelName) {
        final NotificationChannel channel = new NotificationChannel(channelID, channelName, NotificationManager.IMPORTANCE_HIGH);
        channel.setLightColor(Color.GREEN);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        final NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }
}