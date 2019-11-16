package de.mel.filesync.index.watchdog;


import de.mel.Lok;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.auth.tools.N;
import de.mel.filesync.service.MelFileSyncService;
import org.jdeferred.Promise;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

/**
 * Created by xor on 2/6/17.
 */

public abstract class FileWatcherPC extends FileWatcher {
    protected final boolean useSymLinks;
    protected WatchService watchService;
    protected WatchEvent.Kind<?>[] KINDS = new WatchEvent.Kind<?>[]{StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE};


    public FileWatcherPC(MelFileSyncService melFileSyncService, String name, WatchService watchService) {
        super(melFileSyncService);
        this.name = name;
        this.watchService = watchService;
        this.setStageIndexer(melFileSyncService.getStageIndexer());
        this.transferDirectoryPath = melFileSyncService.getFileSyncSettings().getTransferDirectory().getAbsolutePath();
        this.useSymLinks = melFileSyncService.getFileSyncSettings().getUseSymLinks();
    }

    protected void analyze(WatchEvent<?> event, AbstractFile file) {
        try {
            if (melFileSyncService.getMelAuthService().getPowerManager().heavyWorkAllowed()) {
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
            Lok.debug("IndexWatchdogListener[" + melFileSyncService.getFileSyncSettings().getRole() + "].analyze[" + event.kind() + "]: " + file.getAbsolutePath());
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
        return getClass().getSimpleName() + " for " + melFileSyncService.getRunnableName();
    }

    @Override
    public void watchDirectory(IFile dir) throws IOException {
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
