package de.mein.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.widget.Toast;

import java.util.stream.Stream;

import de.mein.R;

/**
 * Created by xor on 8/25/17.
 */

public class Notifier {

    public static final String CHANNEL_ID_SOUND = "dx33rm";
    public static final String CHANNEL_ID_SILENT = "d44op";

    public static NotificationManagerCompat createNotificationManager(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID_SOUND);
            if (channel == null) {
                channel = new NotificationChannel(CHANNEL_ID_SOUND, "bla", NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("mein description goes here");
                channel.enableLights(true);
                channel.setLightColor(Color.GREEN);
                manager.createNotificationChannel(channel);
            }
            channel = manager.getNotificationChannel(CHANNEL_ID_SILENT);
            if (channel == null) {
                channel = new NotificationChannel(CHANNEL_ID_SILENT, "blubb", NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("mein description goes here");
                channel.enableLights(false);
                channel.setSound(null, null);
                manager.createNotificationChannel(channel);
            }
        }
        return NotificationManagerCompat.from(context);
    }


    public static void cancel(Intent intent, int requestCode) {
        Context context = Tools.getApplicationContext();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        if (pendingIntent != null)
            pendingIntent.cancel();
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancel(requestCode);
    }

    public static void toast(Context context, int msgId){
        toast(context,context.getString(msgId));
    }
    public static void toast(Context context, CharSequence message) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            Toast toast = Toast.makeText(context, message, Toast.LENGTH_LONG);
            toast.show();
        });
    }

    public static void shortToast(Context context, CharSequence message) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            Toast toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
            toast.show();
        });
    }

    public static void pendingNotificationWithIcon(int requestCode, int iconResource, Intent intent, @NonNull String channelId, CharSequence title, CharSequence text, CharSequence ticker) {
        Context context = Tools.getApplicationContext();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = builder.setSmallIcon(iconResource)
                .setContentTitle(title)
                .setContentText(text)
                .setTicker(ticker)
                .setContentIntent(pendingIntent)
                .build();
        Notifier.createNotificationManager(context).notify(requestCode, notification);
    }

    public static void notification(int requestCode, int iconResource, @NonNull String channelId, CharSequence title, CharSequence text, CharSequence ticker) {
        Context context = Tools.getApplicationContext();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId);
        Notification notification = builder.setSmallIcon(iconResource)
                .setContentTitle(title)
                .setContentText(text)
                .setTicker(ticker)
                .build();
        Notifier.createNotificationManager(context).notify(requestCode, notification);
    }

    public static void pendingNotification(int requestCode, Intent intent, @NonNull String channelId, CharSequence title, CharSequence text, CharSequence ticker) {
        pendingNotificationWithIcon(requestCode, R.drawable.icon_notification_2, intent, channelId, title, text, ticker);
    }

}
