package de.mel.android.filesync;

import android.Manifest;
import android.app.Activity;
import android.content.Context;

import androidx.core.app.NotificationCompat;

import android.os.Build;
import android.view.ViewGroup;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.mel.AndroidPermission;
import de.mel.R;
import de.mel.android.MainActivity;
import de.mel.android.Notifier;
import de.mel.android.Tools;
import de.mel.android.controller.AndroidServiceGuiController;
import de.mel.android.Threadder;
import de.mel.android.filesync.controller.RemoteFileSyncServiceChooserGuiController;
import de.mel.android.filesync.controller.AndroidFileSyncEditGuiController;
import de.mel.auth.MelNotification;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.auth.service.IMelService;
import de.mel.auth.service.MelAuthService;
import de.mel.android.boot.AndroidBootLoader;
import de.mel.auth.tools.F;
import de.mel.auth.tools.N;
import de.mel.filesync.FileSyncBootloader;
import de.mel.filesync.FileSyncCreateServiceHelper;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.service.MelFileSyncService;

/**
 * Created by xor on 2/25/17.
 */

public class AndroidFileSyncBootloader extends FileSyncBootloader implements AndroidBootLoader {
    private static final int PERMISSION_WRITE = 666;

    @Override
    public void cleanUpDeletedService(MelFileSyncService melService, String uuid) {
        super.cleanUpDeletedService(melService, uuid);
        Tools.getApplicationContext().deleteDatabase("service." + uuid + "." + FileSyncStrings.DB_FILENAME);
        File instanceDir = new File(bootLoaderDir, melService.getUuid());
        N.r(() -> BashTools.Companion.rmRf(AbstractFile.instance(instanceDir)));
    }

    /**
     * Bootloaders extending this class probably want another ServiceHelper
     *
     * @param melAuthService
     * @return
     */
    protected FileSyncCreateServiceHelper createCreateServiceHelper(MelAuthService melAuthService) {
        return new FileSyncCreateServiceHelper(melAuthService);

    }

    @Override
    public void createService(Activity activity, MelAuthService melAuthService, AndroidServiceGuiController currentController) {
        RemoteFileSyncServiceChooserGuiController driveCreateGuiController = (RemoteFileSyncServiceChooserGuiController) currentController;

        // create the actual MelDrive service
        FileSyncCreateServiceHelper fileSyncCreateServiceHelper = createCreateServiceHelper(melAuthService);
        if (driveCreateGuiController.isValid())
            Threadder.runNoTryThread(() -> {
                String name = driveCreateGuiController.getName();
                IFile rootFile = driveCreateGuiController.getRootFile();
                if (driveCreateGuiController.isServer()) {
                    fileSyncCreateServiceHelper.createServerService(name, rootFile, driveCreateGuiController.getWastebinRatio(), driveCreateGuiController.getMaxDays(), false);
                } else {
                    {// todo debug
                        System.out.println("AndroidFileSyncBootloader.createService.DEBUBG");
                        File subDir = new File(driveCreateGuiController.getRootFile().getAbsolutePath(), "sub1");
                        subDir.mkdirs();
                        File sub1txt = new File(subDir, "sub1.txt");
                        Files.write(sub1txt.toPath(), "android".getBytes());
                    }
                    Long certId = driveCreateGuiController.getSelectedCertId();
                    String serviceUuid = driveCreateGuiController.getSelectedService().getUuid().v();
                    fileSyncCreateServiceHelper.createClientService(name, rootFile, certId, serviceUuid, driveCreateGuiController.getWastebinRatio(), driveCreateGuiController.getMaxDays(), false);
                    //promise.done(melDriveClientService -> N.r(() -> melDriveClientService.syncThisClient()));
                }
            });
    }

    @Override
    public AndroidServiceGuiController inflateEmbeddedView(ViewGroup embedded, MainActivity activity, MelAuthService melAuthService, IMelService runningInstance) {
        if (runningInstance == null) {
            return new RemoteFileSyncServiceChooserGuiController(melAuthService, activity, embedded, this);
        } else {
            return new AndroidFileSyncEditGuiController(melAuthService, activity, runningInstance, embedded);
        }
    }

    @Override
    public AndroidPermission[] getPermissions() {

        // todo WRITE_EXTERNAL_STORAGE does not do anything in android >=11
        List<AndroidPermission> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            permissions.add(new AndroidPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE, R.string.permissionsExplainAllTitle, R.string.permissionExplainAllText));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(new AndroidPermission(Manifest.permission.POST_NOTIFICATIONS, R.string.permissionsExplainNotificationsTitle, R.string.permissionsExplainNotificationsText));
            }
        } else {
            permissions.add(new AndroidPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, R.string.permissionRequiredTitle, R.string.permissionDriveWriteMessage));
            permissions.add(new AndroidPermission(Manifest.permission.READ_EXTERNAL_STORAGE, R.string.permissionRequiredTitle, R.string.permissionDriveWriteMessage));
        }
//        permissions.add(new AndroidPermission(Manifest.permission.ACCESS_FINE_LOCATION, R.string.permissionsDebugTitle, R.string.permissionsDebugText));
        return permissions.toArray(permissions.toArray(new AndroidPermission[0]));
    }

    @Override
    public int getMenuIcon() {
        return R.drawable.icon_notification_drive_legacy;
    }

    @Override
    public NotificationCompat.Builder createNotificationBuilder(Context context, IMelService melService, MelNotification melNotification) {
        String intention = melNotification.getIntention();
        if (intention.equals(FileSyncStrings.Notifications.INTENTION_PROGRESS)
                || intention.equals(FileSyncStrings.Notifications.INTENTION_BOOT)) {
            return new NotificationCompat.Builder(context, Notifier.CHANNEL_ID_SILENT);
        }
        return new NotificationCompat.Builder(context, Notifier.CHANNEL_ID_SOUND);
    }

    @Override
    public Class createNotificationActivityClass(IMelService melService, MelNotification melNotification) {
        String intention = melNotification.getIntention();
        if (intention.equals(FileSyncStrings.Notifications.INTENTION_PROGRESS)
                || intention.equals(FileSyncStrings.Notifications.INTENTION_BOOT)) {
            return MainActivity.class;
        } else if (intention.equals(FileSyncStrings.Notifications.INTENTION_CONFLICT_DETECTED))
            return FileSyncConflictsPopupActivity.class;
        return null;
    }


}
