package de.mein.android.drive.boot;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;

import org.jdeferred.Promise;

import de.mein.android.controller.AndroidServiceCreatorGuiController;
import de.mein.android.Threadder;
import de.mein.android.drive.controller.AndroidDriveCreateGuiController;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.NoTryRunner;
import de.mein.android.boot.AndroidBootLoader;
import de.mein.drive.DriveBootLoader;
import de.mein.drive.DriveCreateController;
import de.mein.drive.service.MeinDriveClientService;
import mein.de.meindrive.R;

/**
 * Created by xor on 2/25/17.
 */

public class AndroidDriveBootLoader extends DriveBootLoader implements AndroidBootLoader {
    private static final int PERMISSION_WRITE = 666;

    @Override
    public Integer getCreateResource() {
        return R.layout.embedded_create_drive;
    }

    @Override
    public Integer getEditResource(IMeinService service) {
        return R.layout.embedded_create_drive;
    }

    @Override
    public void createService(Activity activity, MeinAuthService meinAuthService, AndroidServiceCreatorGuiController currentController) {
        AndroidDriveCreateGuiController driveCreateGuiController = (AndroidDriveCreateGuiController) currentController;

        // create the actual MeinDrive service
        DriveCreateController driveCreateController = new DriveCreateController(meinAuthService);
        if (driveCreateGuiController.isValid())
            Threadder.runNoTryThread(() -> {
                String name = driveCreateGuiController.getName();
                String path = driveCreateGuiController.getPath();
                if (driveCreateGuiController.isServer()) {
                    driveCreateController.createDriveServerService(name, path);
                } else {
                    Long certId = driveCreateGuiController.getSelectedCertId();
                    String serviceUuid = driveCreateGuiController.getSelectedDrive().getUuid().v();
                    Promise<MeinDriveClientService, Exception, Void> promise = driveCreateController.createDriveClientService(name, path, certId, serviceUuid);
                    promise.done(meinDriveClientService -> NoTryRunner.run(() -> meinDriveClientService.syncThisClient()));
                }
            });
    }

    @Override
    public AndroidServiceCreatorGuiController createGuiController(MeinAuthService meinAuthService, Activity activity, View rootView) {
        // check for permission if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            int permission = ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE);
            if (permission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_WRITE);
            }
            System.out.println(permission);
        }
        return new AndroidDriveCreateGuiController(meinAuthService, activity, rootView);
    }
}
