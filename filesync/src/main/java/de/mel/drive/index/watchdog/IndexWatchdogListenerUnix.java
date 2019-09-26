package de.mel.drive.index.watchdog;

import de.mel.Lok;
import de.mel.auth.file.AFile;
import de.mel.drive.data.PathCollection;
import de.mel.drive.service.MelDriveService;
import org.jdeferred.Promise;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/**
 * Created by xor on 7/11/16.
 */
@SuppressWarnings("Duplicates")
class IndexWatchdogListenerUnix extends IndexWatchdogListenerPC {
    // we
    private final UnixReferenceFileHandler unixReferenceFileHandler;
    private boolean firstRun = true;

    IndexWatchdogListenerUnix(MelDriveService melDriveService, WatchService watchService) {
        super(melDriveService, "IndexWatchdogListenerUnix", watchService);
        unixReferenceFileHandler = new UnixReferenceFileHandler(melDriveService.getServiceInstanceWorkingDirectory(), melDriveService.getDriveSettings().getRootDirectory().getOriginalFile(), AFile.instance(melDriveService.getDriveSettings().getTransferDirectory().getAbsolutePath()));
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
                            AFile file = AFile.instance(absolutePath);
                            Lok.debug("IndexWatchdogListener[" + melDriveService.getDriveSettings().getRole() + "].got event[" + event.kind() + "] for: " + absolutePath);
                            if (event.kind().equals(StandardWatchEventKinds.ENTRY_CREATE)) {
                                // start the timer but do not analyze. Sometimes we get the wrong WatchKey so we cannot trust it.
                                if (melDriveService.getMelAuthService().getPowerManager().heavyWorkAllowed())
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
            List<AFile<?>> paths = unixReferenceFileHandler.stuffModifiedAfter();
            pathCollection.addAll(paths);
            for (AFile f : paths) {
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
