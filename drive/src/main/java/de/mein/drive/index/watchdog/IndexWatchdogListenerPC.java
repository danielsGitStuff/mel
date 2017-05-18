package de.mein.drive.index.watchdog;

import de.mein.auth.tools.N;
import de.mein.drive.service.MeinDriveService;

import java.nio.file.*;

/**
 * Created by xor on 2/6/17.
 */

public abstract class IndexWatchdogListenerPC extends IndexWatchdogListener {
    protected WatchService watchService;
    protected WatchEvent.Kind<?>[] KINDS = new WatchEvent.Kind<?>[]{StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE};

    public IndexWatchdogListenerPC(MeinDriveService meinDriveService, String name, WatchService watchService) {
        this.name = name;
        this.watchService = watchService;
        this.meinDriveService = meinDriveService;
        this.setStageIndexer(meinDriveService.getStageIndexer());
        this.transferDirectoryPath = meinDriveService.getDriveSettings().getTransferDirectoryPath();
    }



    @Override
    public void onShutDown() {
        N.r(() -> watchService.close());
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + meinDriveService.getRunnableName();
    }
}
