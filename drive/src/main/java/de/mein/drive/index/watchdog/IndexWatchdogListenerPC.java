package de.mein.drive.index.watchdog;


import de.mein.Lok;
import de.mein.auth.file.AFile;
import de.mein.auth.tools.N;
import de.mein.drive.service.MeinDriveService;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

/**
 * Created by xor on 2/6/17.
 */

public abstract class IndexWatchdogListenerPC extends IndexWatchdogListener {
    protected final boolean useSymLinks;
    protected WatchService watchService;
    protected WatchEvent.Kind<?>[] KINDS = new WatchEvent.Kind<?>[]{StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE};


    public IndexWatchdogListenerPC(MeinDriveService meinDriveService, String name, WatchService watchService) {
        super(meinDriveService);
        this.name = name;
        this.watchService = watchService;
        this.setStageIndexer(meinDriveService.getStageIndexer());
        this.transferDirectoryPath = meinDriveService.getDriveSettings().getTransferDirectory().getAbsolutePath();
        this.useSymLinks = meinDriveService.getDriveSettings().getUseSymLinks();
    }

    protected void analyze(WatchEvent<?> event, AFile file) {
        try {
            if (meinDriveService.getMeinAuthService().getPowerManager().heavyWorkAllowed()) {
                watchDogTimer.start();
            }else
                surpressEvent();
            if (event.kind().equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
                // figure out whether or not writing to the file is still in progress
                try {
                    double r = Math.random();
                    Lok.debug("IndexWatchdogListener.analyze.attempt to open " + file.getAbsolutePath() + " " + r);
                    InputStream is = file.inputStream();
                    is.close();
                    Lok.debug("IndexWatchdogListener.analyze.success " + r);
                    watchDogTimer.resume();
                } catch (FileNotFoundException e) {
                    Lok.debug("IndexWatchdogListener.analyze.file not found: " + file.getAbsolutePath());
                } catch (Exception e) {
                    Lok.debug("IndexWatchdogListener.analyze.writing in progress");
                    watchDogTimer.waite();
                }
            } else if (event.kind().equals(StandardWatchEventKinds.ENTRY_CREATE) && file.exists() && file.isDirectory()) {
                this.watchDirectory(file);
            }
            Lok.debug("IndexWatchdogListener[" + meinDriveService.getDriveSettings().getRole() + "].analyze[" + event.kind() + "]: " + file.getAbsolutePath());
            pathCollection.addPath(file);
        } catch (Exception e) {
            e.printStackTrace();
            // todo check for inotify exceeded. if so, stop the service
        }
    }


    @Override
    public void onShutDown() {
        N.r(() -> watchService.close());
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + meinDriveService.getRunnableName();
    }

    @Override
    public void watchDirectory(AFile dir) throws IOException {
        try {
            Path path = Paths.get(dir.getAbsolutePath());
            if (Files.isSymbolicLink(path))
                return;
            path.register(watchService, KINDS);
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }
}
