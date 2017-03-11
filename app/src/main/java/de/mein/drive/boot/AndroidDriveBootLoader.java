package de.mein.drive.boot;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;

import de.mein.AndroidServiceCreatorGuiController;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.NoTryRunner;
import de.mein.boot.AndroidBootLoader;
import de.mein.drive.DriveBootLoader;
import de.mein.drive.DriveCreateController;
import de.mein.drive.controller.AndroidDriveCreateGuiController;
import mein.de.meindrive.R;

/**
 * Created by xor on 2/25/17.
 */

public class AndroidDriveBootLoader extends DriveBootLoader implements AndroidBootLoader {
    private static final int PERMISSION_WRITE = 666;

    private AndroidDriveCreateGuiController driveCreateGuiController;

    @Override
    public Integer getCreateResource() {
        return R.layout.create_drive;
    }

    @Override
    public Integer getEditResource(IMeinService service) {
        return R.layout.create_drive;
    }

    @Override
    public void createService(Activity activity, MeinAuthService meinAuthService) {
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
        // create the actual MeinDrive service
        DriveCreateController driveCreateController = new DriveCreateController(meinAuthService);
        if (driveCreateGuiController.isValid())
            NoTryRunner.run(() -> {
                String name = driveCreateGuiController.getName();
                String path = driveCreateGuiController.getPath();
                if (driveCreateGuiController.isServer()) {
                    driveCreateController.createDriveServerService(name, path);
                } else {
                    Long certId = 666L;
                    String serviceUuid = "shit";
                    driveCreateController.createDriveClientService(name, path, certId, serviceUuid);
                }
            });

    }

    @Override
    public AndroidServiceCreatorGuiController createGuiController(MeinAuthService meinAuthService, Activity activity, View rootView) {
        this.driveCreateGuiController = new AndroidDriveCreateGuiController(meinAuthService, activity, rootView);
        return driveCreateGuiController;
    }
}
