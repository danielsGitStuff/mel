package de.mein.drive.index.watchdog;


import de.mein.Lok;
import de.mein.auth.file.AFile;
import de.mein.auth.tools.N;
import de.mein.drive.bash.AutoKlausIterator;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.bash.BashTools;
import de.mein.drive.data.PathCollection;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.sql.FsDirectory;

import java.io.File;
import java.nio.file.*;
import java.util.Iterator;

/**
 * Created by xor on 12.08.2016.
 */
public class IndexWatchDogListenerWindows extends IndexWatchdogListenerPC {

    private boolean watchesRoot = false;
    private long latestTimeStamp = System.currentTimeMillis();

    public IndexWatchDogListenerWindows(MeinDriveService meinDriveService, WatchService watchService) {
        super(meinDriveService, "IndexWatchDogListenerWindows", watchService);
    }



    @Override
    public void onTimerStopped() {
        Lok.debug("IndexWatchdogListener.onTimerStopped");
        long newTimeStamp = System.currentTimeMillis();
        long timeStamp = latestTimeStamp;
        latestTimeStamp =newTimeStamp;
        PathCollection pathCollection = new PathCollection();
        N.r(() -> {
            DriveSettings driveSettings = meinDriveService.getDriveSettings();
            try (AutoKlausIterator<AFile<?>> paths = BashTools.stuffModifiedAfter(driveSettings.getRootDirectory().getOriginalFile(), driveSettings.getTransferDirectoryFile(), timeStamp)){
                while (paths.hasNext()) {
                    AFile path = paths.next();
                    Lok.debug("   IndexWatchDogListenerWindows.onTimerStopped: " + path);
                    pathCollection.addPath(path);
                }
            }
        });
        stageIndexer.examinePaths(this, pathCollection);
    }


    @SuppressWarnings("Duplicates")
    @Override
    public void runImpl() {
        try {
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
                            Lok.debug("IndexWatchdogListener[" + meinDriveService.getDriveSettings().getRole() + "].got event[" + event.kind() + "] for: " + absolutePath);
                            if (event.kind().equals(StandardWatchEventKinds.ENTRY_CREATE)) {
                                // start the timer but do not analyze. Sometimes we get the wrong WatchKey so we cannot trust it.
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


}
