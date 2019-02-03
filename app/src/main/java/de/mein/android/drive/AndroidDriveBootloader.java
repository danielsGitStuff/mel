package de.mein.android.drive;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import androidx.core.app.NotificationCompat;

import android.view.ViewGroup;

import org.jdeferred.Promise;

import de.mein.R;
import de.mein.android.MainActivity;
import de.mein.android.MeinActivity;
import de.mein.android.Notifier;
import de.mein.android.controller.AndroidServiceGuiController;
import de.mein.android.Threadder;
import de.mein.android.drive.controller.RemoteDriveServiceChooserGuiController;
import de.mein.android.drive.controller.AndroidDriveEditGuiController;
import de.mein.auth.MeinNotification;
import de.mein.auth.file.AFile;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.android.boot.AndroidBootLoader;
import de.mein.drive.DriveBootloader;
import de.mein.drive.DriveCreateController;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.service.MeinDriveClientService;

/**
 * Created by xor on 2/25/17.
 */

public class AndroidDriveBootloader extends DriveBootloader implements AndroidBootLoader {
    private static final int PERMISSION_WRITE = 666;

    @Override
    public void createService(Activity activity, MeinAuthService meinAuthService, AndroidServiceGuiController currentController) {
        RemoteDriveServiceChooserGuiController driveCreateGuiController = (RemoteDriveServiceChooserGuiController) currentController;

        // create the actual MeinDrive service
        DriveCreateController driveCreateController = new DriveCreateController(meinAuthService);
        if (driveCreateGuiController.isValid())
            Threadder.runNoTryThread(() -> {
                String name = driveCreateGuiController.getName();
                AFile rootFile = driveCreateGuiController.getRootFile();
                if (driveCreateGuiController.isServer()) {
                    driveCreateController.createDriveServerService(name, rootFile, driveCreateGuiController.getWastebinRatio(), driveCreateGuiController.getMaxDays());
                } else {
                    Long certId = driveCreateGuiController.getSelectedCertId();
                    String serviceUuid = driveCreateGuiController.getSelectedService().getUuid().v();
                    Promise<MeinDriveClientService, Exception, Void> promise = driveCreateController.createDriveClientService(name, rootFile, certId, serviceUuid, driveCreateGuiController.getWastebinRatio(), driveCreateGuiController.getMaxDays());
                    //promise.done(meinDriveClientService -> N.r(() -> meinDriveClientService.syncThisClient()));
                }
            });
    }

    @Override
    public AndroidServiceGuiController inflateEmbeddedView(ViewGroup embedded, MeinActivity activity, MeinAuthService meinAuthService, IMeinService runningInstance) {
        if (runningInstance == null) {
            return new RemoteDriveServiceChooserGuiController(meinAuthService, activity, embedded);
        } else {
            return new AndroidDriveEditGuiController(meinAuthService, activity, runningInstance, embedded);
        }
    }

    @Override
    public String[] getPermissions() {
        return new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    @Override
    public int getMenuIcon() {
        return R.drawable.icon_notification_drive_legacy;
    }

    @Override
    public NotificationCompat.Builder createNotificationBuilder(Context context, IMeinService meinService, MeinNotification meinNotification) {
        String intention = meinNotification.getIntention();
        if (intention.equals(DriveStrings.Notifications.INTENTION_PROGRESS) || intention.equals(DriveStrings.Notifications.INTENTION_BOOT)) {
            return new NotificationCompat.Builder(context, Notifier.CHANNEL_ID_SILENT);
        }
        return new NotificationCompat.Builder(context, Notifier.CHANNEL_ID_SOUND);
    }

    @Override
    public Class createNotificationActivityClass(IMeinService meinService, MeinNotification meinNotification) {
        String intention = meinNotification.getIntention();
        if (intention.equals(DriveStrings.Notifications.INTENTION_PROGRESS) || intention.equals(DriveStrings.Notifications.INTENTION_BOOT)) {
            return MainActivity.class;
        } else if (intention.equals(DriveStrings.Notifications.INTENTION_CONFLICT_DETECTED))
            return DriveConflictsPopupActivity.class;
        return null;
    }


}
