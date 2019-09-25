package de.mel.android.service;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import de.mel.Lok;
import de.mel.R;
import de.mel.android.Notifier;
import de.mel.android.Tools;
import de.mel.android.boot.AndroidBootLoader;
import de.mel.auth.MelAuthAdmin;
import de.mel.auth.MelNotification;
import de.mel.auth.MelStrings;
import de.mel.auth.service.Bootloader;
import de.mel.auth.service.IMelService;
import de.mel.auth.service.MelAuthService;
import de.mel.drive.data.DriveStrings;
import de.mel.sql.SqlQueriesException;

/**
 * Created by xor on 07.08.2017.
 */

public class AndroidAdmin implements MelAuthAdmin {

    private final Context context;
    private final NotificationManagerCompat notificationManager;
    private MelAuthService melAuthService;

    public AndroidAdmin(Context context) {
        this.context = context;
        notificationManager = Notifier.createNotificationManager(context);
    }

    @Override
    public void start(MelAuthService melAuthService) {
        this.melAuthService = melAuthService;
    }

    @Override
    public void onNotificationFromService(IMelService melService, MelNotification melNotification) {
        int requestCode = Tools.generateIntentRequestCode();
        String intention = melNotification.getIntention();
        Class activityClass = null;
        NotificationCompat.Builder builder = null;
        int icon = R.mipmap.ic_launcher;
        try {
            Bootloader bootLoader = melAuthService.getMelBoot().getBootLoader(melService);
            if (bootLoader instanceof AndroidBootLoader) {
                AndroidBootLoader androidBootLoader = (AndroidBootLoader) bootLoader;
                builder = androidBootLoader.createNotificationBuilder(context, melService, melNotification);
                activityClass = androidBootLoader.createNotificationActivityClass(melService, melNotification);
                icon = androidBootLoader.getMenuIcon();
            } else {
                builder = new NotificationCompat.Builder(context, Notifier.CHANNEL_ID_SOUND);
            }
        } catch (SqlQueriesException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        if (builder == null) {
            System.err.println("NotificationBuilder was null :(");
            return;
        }
        Intent intent = new Intent(context, activityClass);
        intent.putExtra(MelStrings.Notifications.SERVICE_UUID, melNotification.getServiceUuid());
        intent.putExtra(MelStrings.Notifications.INTENTION, intention);
        intent.putExtra(MelStrings.Notifications.REQUEST_CODE, requestCode);
        for (String key : melNotification.getSerializedExtraKeys()) {
            intent.putExtra(MelStrings.Notifications.EXTRA + key, melNotification.getSerializedExtra(key));
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setSmallIcon(icon)
                .setContentTitle(melNotification.getTitle())
                .setContentText(melAuthService.getCompleteNotificationText(melNotification))
                .setContentIntent(pendingIntent);
        NotificationCompat.Builder finalBuilder = builder;
        melNotification.addProgressListener(new MelNotification.MelProgressListener() {
            @Override
            public void onProgress(MelNotification notification, int max, int current, boolean indeterminate) {
                finalBuilder.setProgress(max, current, indeterminate);
                finalBuilder.setContentTitle(melNotification.getTitle())
                        .setContentText(melNotification.getText());
                notificationManager.notify(requestCode, finalBuilder.build());
            }

            @Override
            public void onCancel(MelNotification notification) {
                Notifier.cancel(intent, requestCode);
            }

            @Override
            public void onFinish(MelNotification notification) {
                finalBuilder.setProgress(1, 1, false);
                finalBuilder.setContentTitle(melNotification.getTitle())
                        .setContentText(melNotification.getText());
                notificationManager.notify(requestCode, finalBuilder.build());
            }
        });
        if (!melNotification.isUserCancelable()) {
            builder.setOngoing(true);
        }
        notificationManager.notify(requestCode, builder.build());
    }

    @Override
    public void onChanged() {
        Lok.debug();
    }

    @Override
    public void shutDown() {

    }

    @Override
    public void onProgress(MelNotification notification, int max, int current, boolean indeterminate) {
        // nothing to do here. work is done in onNotificationFromService()
    }

    @Override
    public void onCancel(MelNotification notification) {
        // nothing to do here. work is done in onNotificationFromService()
    }

    @Override
    public void onFinish(MelNotification notification) {
        // nothing to do here. work is done in onNotificationFromService()
    }
}
