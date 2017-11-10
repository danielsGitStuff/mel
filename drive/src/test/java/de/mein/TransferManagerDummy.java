package de.mein;

import de.mein.auth.service.MeinAuthService;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.service.WasteBin;
import de.mein.drive.service.sync.SyncHandler;
import de.mein.drive.sql.dao.TransferDao;
import de.mein.drive.transfer.TransferManager;

/**
 * Created by xor on 10.11.2017.
 */

public class TransferManagerDummy extends TransferManager {
    public TransferManagerDummy() {
        super(null,null,null,null,null);
    }
}
