package de.mein.drive;

import de.mein.auth.service.MeinAuthService;
import de.mein.drive.service.MeinDriveClientService;

import java.io.File;

/**
 * Created by xor on 5/30/17.
 */
public class MeinDriveFXClientService extends MeinDriveClientService {
    public MeinDriveFXClientService(MeinAuthService meinAuthService, File workingDirectory) {
        super(meinAuthService, workingDirectory);
    }
}
