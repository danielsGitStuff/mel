package de.mein.drive.boot;

import de.mein.auth.service.IMeinService;
import de.mein.boot.AndroidBootLoader;
import de.mein.drive.DriveBootLoader;
import mein.de.meindrive.R;

/**
 * Created by xor on 2/25/17.
 */

public class AndroidDriveBootLoader extends DriveBootLoader implements AndroidBootLoader {

    @Override
    public Integer getCreateResource() {
        return R.layout.create_drive;
    }

    @Override
    public Integer getEditResource(IMeinService service) {
        return R.layout.create_drive;
    }
}
