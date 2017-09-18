package de.mein.android.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.security.SecureRandom;

import de.mein.R;
import de.mein.android.MainActivity;
import de.mein.android.Notifier;
import de.mein.android.Tools;
import de.mein.android.boot.AndroidBootLoader;
import de.mein.android.drive.ConflictsPopupActivity;
import de.mein.auth.MeinAuthAdmin;
import de.mein.auth.MeinNotification;
import de.mein.auth.MeinStrings;
import de.mein.auth.data.db.Service;
import de.mein.auth.service.BootLoader;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinService;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.sql.SqlQueriesException;

/**
 * Created by xor on 07.08.2017.
 */

public class AndroidAdmin implements MeinAuthAdmin {

    private final Context context;
    private final NotificationManagerCompat notificationManager;
    private MeinAuthService meinAuthService;

    public AndroidAdmin(Context context) {
        this.context = context;
        notificationManager = Notifier.createNotificationManager(context);
    }

    @Override
    public void start(MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
    }

    @Override
    public void onNotificationFromService(IMeinService meinService, MeinNotification meinNotification) {
        int requestCode = Tools.generateIntentRequestCode();
        String intention = meinNotification.getIntention();
        Class activityClass = null;
        NotificationCompat.Builder builder;
        if (intention.equals(DriveStrings.Notifications.INTENTION_CONFLICT_DETECTED))
            activityClass = ConflictsPopupActivity.class;
        builder = new NotificationCompat.Builder(context, Notifier.CHANNEL_ID_SOUND);

        if (intention.equals(DriveStrings.Notifications.INTENTION_PROGRESS) || intention.equals(DriveStrings.Notifications.INTENTION_BOOT)) {
            activityClass = MainActivity.class;
            builder = new NotificationCompat.Builder(context, Notifier.CHANNEL_ID_SILENT);
        }
        // get the icon
        int icon = R.drawable.ic_menu_add;
        try {
            BootLoader bootLoader = meinAuthService.getMeinBoot().getBootLoader(meinService);
            icon = bootLoader instanceof AndroidBootLoader ? ((AndroidBootLoader) bootLoader).getMenuIcon() : icon;
        } catch (SqlQueriesException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        Intent intent = new Intent(context, activityClass);
        intent.putExtra(MeinStrings.Notifications.SERVICE_UUID, meinNotification.getServiceUuid());
        intent.putExtra(MeinStrings.Notifications.INTENTION, intention);
        intent.putExtra(DriveStrings.Notifications.REQUEST_CODE, requestCode);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setSmallIcon(icon)
                .setContentTitle(meinNotification.getTitle())
                .setContentText(meinNotification.getText())
                .setContentIntent(pendingIntent);
        if (intention.equals(DriveStrings.Notifications.INTENTION_PROGRESS) || intention.equals(DriveStrings.Notifications.INTENTION_BOOT)) {
            NotificationCompat.Builder finalBuilder = builder;
            meinNotification.addProgressListener(new MeinNotification.MeinProgressListener() {
                @Override
                public void onProgress(int max, int current, boolean indeterminate) {
                    finalBuilder.setProgress(max, current, indeterminate);
                    finalBuilder.setContentTitle(meinNotification.getTitle())
                            .setContentText(meinNotification.getText());
                    notificationManager.notify(requestCode, finalBuilder.build());
                }

                @Override
                public void cancel() {
                    Notifier.cancel(context, intent, requestCode);
                }

                @Override
                public void finish() {
                    finalBuilder.setProgress(1, 1, false);
                    finalBuilder.setContentTitle(meinNotification.getTitle())
                            .setContentText(meinNotification.getText());
                    notificationManager.notify(requestCode, finalBuilder.build());
                }
            });
        }
        notificationManager.notify(requestCode, builder.build());
    }

    @Override
    public void onChanged() {

    }

    @Override
    public void shutDown() {

    }
}
