package de.mein.drive.boot;

import de.mein.auth.MeinNotification;
import de.mein.auth.boot.BootLoaderFX;
import de.mein.auth.service.IMeinService;
import de.mein.drive.DriveBootLoader;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.service.MeinDriveServerService;
import de.mein.drive.service.MeinDriveService;

/**
 * Created by xor on 9/21/16.
 */
public class DriveFXBootLoader extends DriveBootLoader implements BootLoaderFX<MeinDriveService> {

    @Override
    public String getCreateFXML() {
        return "de/mein/drive/create.embedded.fxml";
    }

    @Override
    public boolean embedCreateFXML() {
        return true;
    }


    @Override
    public String getEditFXML(MeinDriveService meinService) {
        return (meinService instanceof MeinDriveServerService) ? "de/mein/drive/editserver.fxml" : "de/mein/drive/editclient.fxml";
    }

    @Override
    public String getPopupFXML(IMeinService meinService, MeinNotification meinNotification) {
        if (meinNotification.getIntention().equals(DriveStrings.Notifications.INTENTION_CONFLICT_DETECTED)) {
            return "de/mein/drive/popupconflict.fxml";
        }
        if (meinNotification.getIntention().equals(DriveStrings.Notifications.INTENTION_PROGRESS)
                || meinNotification.getIntention().equals(DriveStrings.Notifications.INTENTION_BOOT)) {
            return "de/mein/auth/progress.fxml";
        }
        return null;
    }

    @Override
    public String getIconURL() {
        return "de/mein/drive/drive.png";
    }
}
