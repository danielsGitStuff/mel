package de.mein.drive.boot;

import android.view.View;

import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.boot.AndroidBootLoader;
import de.mein.drive.DriveBootLoader;

/**
 * Created by xor on 2/25/17.
 */

public class AndroidDriveBootLoader extends DriveBootLoader implements AndroidBootLoader {

    @Override
    public String getCreateResource() {
        return null;
    }

    @Override
    public String getEditResource(IMeinService service) {
        return null;
    }
}
