package de.mel.drive.boot;

import de.mel.auth.MelNotification;
import de.mel.auth.boot.BootLoaderFX;
import de.mel.auth.service.IMelService;
import de.mel.drive.DriveBootloader;
import de.mel.drive.data.DriveStrings;
import de.mel.drive.service.MelDriveServerService;
import de.mel.drive.service.MelDriveService;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Created by xor on 9/21/16.
 */
public class DriveFXBootloader extends DriveBootloader implements BootLoaderFX<MelDriveService> {

    @Override
    public String getCreateFXML() {
        return "de/mel/filesyncfx/create.embedded.fxml";
    }

    @Override
    public boolean embedCreateFXML() {
        return true;
    }


    @Override
    public String getEditFXML(MelDriveService melService) {
        return (melService instanceof MelDriveServerService) ? "de/mel/filesyncfx/editserver.fxml" : "de/mel/filesyncfx/editclient.fxml";
    }

    @Override
    public String getPopupFXML(IMelService melService, MelNotification melNotification) {
        if (melNotification.getIntention().equals(DriveStrings.Notifications.INTENTION_CONFLICT_DETECTED)) {
            return "de/mel/filesyncfx/popupconflict.olde.fxml";
        }
        if (melNotification.getIntention().equals(DriveStrings.Notifications.INTENTION_PROGRESS)
                || melNotification.getIntention().equals(DriveStrings.Notifications.INTENTION_BOOT)) {
            return "de/mel/auth/progress.fxml";
        }
        return null;
    }

    @Override
    public String getIconURL() {
        return "de/mel/filesyncfx/drive.png";
    }

    @Override
    public ResourceBundle getResourceBundle(Locale locale) {
        return ResourceBundle.getBundle("de/mel/filesyncfx/strings", locale);

    }
}
