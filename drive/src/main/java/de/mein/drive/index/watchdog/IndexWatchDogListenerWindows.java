package de.mein.drive.index.watchdog;

import com.sun.nio.file.ExtendedWatchEventModifier;

import de.mein.auth.file.AFile;
import de.mein.auth.tools.N;
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
    public void foundDirectory(FsDirectory fsDirectory) {

    }

    @Override
    public void onTimerStopped() {
        System.out.println("IndexWatchdogListener.onTimerStopped");
        long newTimeStamp = System.currentTimeMillis();
        long timeStamp = latestTimeStamp;
        latestTimeStamp =newTimeStamp;
        PathCollection pathCollection = new PathCollection();
        N.r(() -> {
            DriveSettings driveSettings = meinDriveService.getDriveSettings();
            Iterator<String> paths = BashTools.stuffModifiedAfter(driveSettings.getRootDirectory().getOriginalFile(), driveSettings.getTransferDirectoryFile(), timeStamp);
            while (paths.hasNext()) {
                String path = paths.next();
                System.out.println("   IndexWatchDogListenerWindows.onTimerStopped: " + path);
                pathCollection.addPath(path);
            }
        });
        stageIndexer.examinePaths(this, pathCollection);
    }

    @Override
    public void watchDirectory(AFile dir) {
        if (!watchesRoot)
            try {
                watchesRoot = true;
                Path path = Paths.get(dir.getAbsolutePath());
                path.register(watchService, KINDS, ExtendedWatchEventModifier.FILE_TREE);
                System.out.println("IndexWatchDogListenerWindows.registerRoot: " + path.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    @SuppressWarnings("Duplicates")
    @Override
    public void runImpl() {
        try {
            while (true) {
                WatchKey watchKey = watchService.take();
                ignoredSemaphore.acquire();
                try {
                    Path keyPath = (Path) watchKey.watchable();
                    for (WatchEvent<?> event : watchKey.pollEvents()) {
                        Path eventPath = (Path) event.context();
                        String absolutePath = keyPath.toString() + File.separator + eventPath.toString();
                        if (!absolutePath.startsWith(transferDirectoryPath)) {
                            AFile file = AFile.instance(absolutePath);
                            // todo debug
                            System.out.println("IndexWatchdogListener[" + meinDriveService.getDriveSettings().getRole() + "].got event[" + event.kind() + "] for: " + absolutePath);
                            if (event.kind().equals(StandardWatchEventKinds.ENTRY_CREATE)) {
                                // start the timer but do not analyze. Sometimes we get the wrong WatchKey so we cannot trust it.
                                watchDogTimer.start();
                                System.out.println("ignored/broken WatchService");
                            } else {
                                analyze(event, file);
                                System.out.println("analyzed");
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
    public void onHeavyWorkForbidden() {

    }
}
