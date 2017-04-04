package de.mein.drive.watchdog;

import de.mein.drive.data.PathCollection;
import de.mein.drive.index.BashTools;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.sql.FsDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by xor on 7/11/16.
 */
@SuppressWarnings("Duplicates")
class IndexWatchdogUnix2 extends IndexWatchdogPC {
    // we
    private final File timeReferenceFile = new File("time");

    IndexWatchdogUnix2(MeinDriveService meinDriveService, WatchService watchService) {
        super(meinDriveService, "IndexWatchdogUnix", watchService);
    }

    @Override
    public void foundDirectory(FsDirectory fsDirectory) {
        try {
            Path path = Paths.get(fsDirectory.getOriginal().getAbsolutePath());
            WatchKey key = path.register(watchService, KINDS);
            System.out.println("IndexWatchdogListener.foundDirectory: " + path.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            Thread.currentThread().setName(name);
            if (timeReferenceFile.exists())
                timeReferenceFile.delete();
            timeReferenceFile.mkdirs();
            /**
             * cause the WatchService sometimes confuses the WatchKeys when creating folders we have to go around that.
             * We will only process all "delete" and "modify" (cause they can be ongoing for some time) events directly.
             * when an Event pops up, we will start the Timer and once it is finished we ask the Bash to show us every new/modified
             * File or Directory.
             */
            while (true) {
                WatchKey watchKey = watchService.take();
                ignoredSemaphore.acquire();
                Path keyPath = (Path) watchKey.watchable();
                for (WatchEvent<?> event : watchKey.pollEvents()) {
                    Path eventPath = (Path) event.context();
                    String absolutePath = keyPath.toString() + File.separator + eventPath.toString();
                    File file = new File(absolutePath);
//                    if (!ignoredMap.containsKey(absolutePath)) {
                    System.out.println("IndexWatchdogListener[" + meinDriveService.getDriveSettings().getRole() + "].got event[" + event.kind() + "] for: " + absolutePath);
                    if (event.kind().equals(StandardWatchEventKinds.ENTRY_CREATE)) {
                        // start the timer but do not analyze. Sometimes we get the wrong WatchKey so we cannot trust it.
                        watchDogTimer.start();
                        System.out.println("ignored");
                    } else {
                        analyze(event, file);
                        System.out.println("analyzed");
                    }
                    watchKey.reset();
                }
                ignoredSemaphore.release();
                // reset the key
                boolean valid = watchKey.reset();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void watchDirectory(File dir) {
        try {
            Path path = Paths.get(dir.getAbsolutePath());
            WatchKey key = path.register(watchService, KINDS);
            System.out.println("IndexWatchdogListener.watchDirectory: " + path.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onTimerStopped() {
        System.out.println("IndexWatchdogListener.onTimerStopped");
        try {
            /**
             * we cannot retrieve all newly created things, so we have to do it now.
             * and watching the directories as well
              */
            List<String> paths = BashTools.stuffModifiedAfter(timeReferenceFile, meinDriveService.getDriveSettings().getRootDirectory().getOriginalFile());
            pathCollection.addAll(paths);
            for (String p : paths) {
                File f = new File(p);
                if (f.exists() && f.isDirectory()) {
                    watchDirectory(f);
                }
            }
            timeReferenceFile.delete();
            timeReferenceFile.mkdirs();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //meinDriveService.addJob(new FsSyncJob(pathCollection));
        stageIndexer.examinePaths(pathCollection);
        pathCollection = new PathCollection();
    }
}
