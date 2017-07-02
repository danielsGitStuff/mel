package de.mein.drive.boot;

import de.mein.auth.boot.BootLoaderFX;
import de.mein.auth.service.IMeinService;
import de.mein.drive.DriveBootLoader;
import de.mein.drive.service.MeinDriveServerService;
import de.mein.drive.service.MeinDriveService;

/**
 * Created by xor on 9/21/16.
 */
public class DriveFXBootLoader extends DriveBootLoader implements BootLoaderFX<MeinDriveService> {

    @Override
    public String getCreateFXML() {
        return "de/mein/drive/create.fxml";
    }


    @Override
    public String getEditFXML(MeinDriveService meinService) {
        return (meinService instanceof MeinDriveServerService) ? "de/mein/drive/editserver.fxml" : "de/mein/drive/editclient.fxml";
    }

    @Override
    public String getPopupFXML(IMeinService meinService, Object dataObject) {
        return "de/mein/drive/popupconflict.olde.fxml";
    }
}
