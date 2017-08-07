package de.mein.android.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import de.mein.R;
import de.mein.android.PopupActivity;
import de.mein.auth.MeinAuthAdmin;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinService;
import de.mein.auth.tools.N;

/**
 * Created by xor on 07.08.2017.
 */

public class AndroidAdmin implements MeinAuthAdmin {

    private final Context context;
    private final String channelId = "dx33rm";
    private NotificationManagerCompat notificationManager;

    public AndroidAdmin(Context context) {
        this.context = context;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(channelId, "bla", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("mein description goes here");
            channel.enableLights(true);
            channel.setLightColor(Color.GREEN);
            manager.createNotificationChannel(channel);
        }
        notificationManager = NotificationManagerCompat.from(context);
    }

    @Override
    public void start(MeinAuthService meinAuthService) {

    }

    @Override
    public void onMessageFromService(MeinService meinService, Object msgObject) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId);
        PendingIntent pendingIntent = PendingIntent.getActivity(context,666,new Intent(context, PopupActivity.class),PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = builder.setSmallIcon(R.drawable.ic_menu_add)
                .setContentTitle("Test x35")
                .setContentText("Test x46")
                .setContentIntent(pendingIntent)
                .build();
        notificationManager.notify(666,notification);
    }

    @Override
    public void onChanged() {

    }

    @Override
    public void shutDown() {

    }
}
