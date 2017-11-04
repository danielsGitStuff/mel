package de.mein.android;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.support.v4.app.NotificationManagerCompat;
import android.widget.Toast;

import java.util.stream.Stream;

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
                channel.setSound(null,null);
                manager.createNotificationChannel(channel);
            }
        }
        return NotificationManagerCompat.from(context);
    }

    public static void cancel(Context context, Intent intent, int requestCode) {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        if (pendingIntent != null)
            pendingIntent.cancel();
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.cancel(requestCode);
    }

    public static void toast(Context context, CharSequence message) {
        Toast toast = Toast.makeText(context, message, Toast.LENGTH_LONG);
        toast.show();
    }
}
