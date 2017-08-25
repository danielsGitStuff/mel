package de.mein.android.drive.boot;

import android.Manifest;
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import org.jdeferred.Promise;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.controller.AndroidServiceCreatorGuiController;
import de.mein.android.Threadder;
import de.mein.android.drive.controller.AndroidDriveCreateGuiController;
import de.mein.android.drive.controller.AndroidDriveEditGuiController;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.android.boot.AndroidBootLoader;
import de.mein.auth.tools.N;
import de.mein.drive.DriveBootLoader;
import de.mein.drive.DriveCreateController;
import de.mein.drive.service.MeinDriveClientService;

/**
 * Created by xor on 2/25/17.
 */

public class AndroidDriveBootLoader extends DriveBootLoader implements AndroidBootLoader {
    private static final int PERMISSION_WRITE = 666;

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
                    promise.done(meinDriveClientService -> N.r(() -> meinDriveClientService.syncThisClient()));
                }
            });
    }

    @Override
    public AndroidServiceCreatorGuiController createGuiController(MeinAuthService meinAuthService, MeinActivity activity, View rootView, IMeinService runningInstance) {
        // check for permission if necessary
        activity.annoyWithPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return new AndroidDriveCreateGuiController(meinAuthService, activity, rootView);
    }

    @Override
    public AndroidServiceCreatorGuiController inflateEmbeddedView(ViewGroup embedded, MeinActivity activity, MeinAuthService meinAuthService, IMeinService runningInstance) {
        View rootView;
        activity.annoyWithPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (runningInstance == null) {
            rootView = View.inflate(activity, R.layout.embedded_create_drive, embedded);
            return new AndroidDriveCreateGuiController(meinAuthService, activity, rootView);
        } else {
            rootView = View.inflate(activity, R.layout.embedded_edit_drive, embedded);
            return new AndroidDriveEditGuiController(meinAuthService,activity,runningInstance,rootView);
        }

    }

}
