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

import java.security.SecureRandom;

import de.mein.R;
import de.mein.android.PopupActivity;
import de.mein.android.boot.AndroidBootLoader;
import de.mein.android.drive.data.AndroidDriveStrings;
import de.mein.auth.MeinAuthAdmin;
import de.mein.auth.MeinNotification;
import de.mein.auth.MeinStrings;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.service.BootLoader;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinService;
import de.mein.drive.data.DriveStrings;

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
    public void onNotificationFromService(MeinService meinService, MeinNotification meinNotification) {
        try {
            MeinAuthService meinAuthService = meinService.getMeinAuthService();
            ServiceType type = meinAuthService.getDatabaseManager().getServiceTypeById(meinService.getServiceTypeId());
            BootLoader bootloader = meinAuthService.getMeinBoot().getBootLoader(type.getType().v());
            int requestCode = new SecureRandom().nextInt();
            String intention = meinNotification.getIntention();
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId);
            Intent intent = new Intent(context, PopupActivity.class);
            intent.putExtra(MeinStrings.Notifications.SERVICE_UUID, meinNotification.getServiceUuid());
            intent.putExtra(MeinStrings.Notifications.INTENTION, intention);
            intent.putExtra(DriveStrings.Notifications.REQUEST_CODE, requestCode);
            if (bootloader instanceof AndroidBootLoader) {
                AndroidBootLoader androidBootLoader = (AndroidBootLoader) bootloader;
                Class activityClass = androidBootLoader.getNotificationConsumerActivityClass(meinService, intention, meinNotification);
                intent.putExtra(AndroidDriveStrings.Notifications.ACTIVITY_CLASS, activityClass);
            }
            PendingIntent pendingIntent = PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notification = builder.setSmallIcon(R.drawable.ic_menu_add)
                    .setContentTitle(meinNotification.getTitle())
                    .setContentText(meinNotification.getText())
                    .setContentIntent(pendingIntent)
                    .build();
            notificationManager.notify(666, notification);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onChanged() {

    }

    @Override
    public void shutDown() {

    }
}
