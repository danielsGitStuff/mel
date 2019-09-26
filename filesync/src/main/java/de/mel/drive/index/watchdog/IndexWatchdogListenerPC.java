package de.mel.drive.index.watchdog;


import de.mel.Lok;
import de.mel.auth.file.AFile;
import de.mel.auth.tools.N;
import de.mel.drive.service.MelDriveService;
import org.jdeferred.Promise;

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


    public IndexWatchdogListenerPC(MelDriveService melDriveService, String name, WatchService watchService) {
        super(melDriveService);
        this.name = name;
        this.watchService = watchService;
        this.setStageIndexer(melDriveService.getStageIndexer());
        this.transferDirectoryPath = melDriveService.getDriveSettings().getTransferDirectory().getAbsolutePath();
        this.useSymLinks = melDriveService.getDriveSettings().getUseSymLinks();
    }

    protected void analyze(WatchEvent<?> event, AFile file) {
        try {
            if (melDriveService.getMelAuthService().getPowerManager().heavyWorkAllowed()) {
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
            Lok.debug("IndexWatchdogListener[" + melDriveService.getDriveSettings().getRole() + "].analyze[" + event.kind() + "]: " + file.getAbsolutePath());
            pathCollection.addPath(file);
        } catch (Exception e) {
            e.printStackTrace();
            // todo check for inotify exceeded. if so, stop the service
        }
    }


    @Override
    public Promise<Void, Void, Void> onShutDown() {
        N.r(() -> watchService.close());
        return null;
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + melDriveService.getRunnableName();
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
