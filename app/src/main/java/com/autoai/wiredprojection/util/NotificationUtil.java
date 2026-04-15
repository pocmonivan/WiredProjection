package com.autoai.wiredprojection.util;

import android.annotation.DrawableRes;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import com.autoai.wiredprojection.R;

public class NotificationUtil {

    public static final String CHANNEL_ID = "AtcCamera_CHANNEL_ID";
    private static final String CHANNEL_NAME = "AtcCamera_CHANNEL_NAME";
    public static final int NOTIFY_ID = 101;

    public static void initChannel(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
        );
        nm.createNotificationChannel(channel);
    }

    public static Notification getDefaultNotification(Context context) {
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.notification_title))
                .setContentText(context.getString(R.string.notification_content))
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        return notification;
    }

    public static void updateNotification(Context context, @DrawableRes int icon, CharSequence title, CharSequence text) {
        initChannel(context);
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(icon)
                .build();
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFY_ID, notification);
    }
}
