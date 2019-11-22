package de.mel.filesync.index.watchdog;

import de.mel.Lok;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.filesync.data.PathCollection;
import de.mel.filesync.service.MelFileSyncService;
import org.jdeferred.Promise;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/**
 * Created by xor on 7/11/16.
 */
@SuppressWarnings("Duplicates")
class FileWatcherUnix extends FileWatcherPC {
    // we
    private final UnixReferenceFileHandler unixReferenceFileHandler;
    private boolean firstRun = true;

    FileWatcherUnix(MelFileSyncService melFileSyncService, WatchService watchService) {
        super(melFileSyncService, "IndexWatchdogListenerUnix", watchService);
        unixReferenceFileHandler = new UnixReferenceFileHandler(melFileSyncService.getServiceInstanceWorkingDirectory(), melFileSyncService.getFileSyncSettings().getRootDirectory().getOriginalFile(), AbstractFile.instance(melFileSyncService.getFileSyncSettings().getTransferDirectory().getAbsolutePath()));
    }

    @Override
    public void runImpl() {
        try {
            if (firstRun)
                unixReferenceFileHandler.onStart();
            firstRun = false;
            /**
             * cause the WatchService sometimes confuses the WatchKeys when creating folders we have to go around that.
             * We will only process all "delete" and "modify" (cause they can be ongoing for some time) events directly.
             * when an Event pops up, we will start the Timer and once it is finished we ask the Bash to show us every new/modified
             * File or Directory.
             */
            while (!isStopped()) {
                WatchKey watchKey = watchService.take();
                ignoredSemaphore.acquire();
                try {
                    Path keyPath = (Path) watchKey.watchable();
                    for (WatchEvent<?> event : watchKey.pollEvents()) {
                        Path eventPath = (Path) event.context();
                        String absolutePath = keyPath.toString() + File.separator + eventPath.toString();
                        if (!absolutePath.startsWith(transferDirectoryPath)) {
                            IFile file = AbstractFile.instance(absolutePath);
                            Lok.debug("IndexWatchdogListener[" + melFileSyncService.getFileSyncSettings().getRole() + "].got event[" + event.kind() + "] for: " + absolutePath);
                            if (event.kind().equals(StandardWatchEventKinds.ENTRY_CREATE)) {
                                // start the timer but do not analyze. Sometimes we get the wrong WatchKey so we cannot trust it.
                                if (melFileSyncService.getMelAuthService().getPowerManager().heavyWorkAllowed())
                                    watchDogTimer.start();
                                Lok.debug("ignored/broken WatchService");
                            } else {
                                analyze(event, file);
                                Lok.debug("analyzed");
                            }
                        }
                        watchKey.reset();
                    }
                } finally {
                    ignoredSemaphore.release();
                }
                // reset the key
                boolean valid = watchKey.reset();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onTimerStopped() {
        Lok.debug("IndexWatchdogListener.onTimerStopped");
        PathCollection pathCollection = new PathCollection();

        try {
            /**
             * we cannot retrieve all newly created things, so we have to do it now.
             * and watching the directories as well
             */
            List<IFile> paths = unixReferenceFileHandler.stuffModifiedAfter();
            pathCollection.addAll(paths);
            for (IFile f : paths) {
                if (f.exists() && f.isDirectory()) {
                    watchDirectory(f);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            // todo check for inotify exceeded. if so, stop the service
        }
        //melDriveService.addJob(new FsSyncJob(pathCollection));
        stageIndexer.examinePaths(this, pathCollection);
        pathCollection = new PathCollection();
    }

    @Override
    public Promise<Void, Void, Void> onShutDown() {
        super.onShutDown();
        return null;
    }

}
