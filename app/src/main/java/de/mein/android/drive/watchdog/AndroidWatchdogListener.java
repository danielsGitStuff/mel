package de.mein.android.drive.watchdog;

import android.os.FileObserver;

import java.io.File;

import de.mein.drive.data.PathCollection;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.sql.FsDirectory;
import de.mein.drive.index.watchdog.IndexWatchdogListener;
import de.mein.drive.index.watchdog.timer.WatchDogTimer;
import de.mein.sql.RWLock;

/**
 * Created by xor on 2/6/17.
 */

public class AndroidWatchdogListener extends IndexWatchdogListener {
    private boolean watchesRoot = false;
    private FileObserver fileObserver;
    private WatchDogTimer watchDogTimer;

    public AndroidWatchdogListener(MeinDriveService meinDriveService) {
        this.meinDriveService = meinDriveService;
        this.setStageIndexer(meinDriveService.getStageIndexer());
        watchDogTimer = new WatchDogTimer(this, 20, 100, 100);
        watchDirectory(meinDriveService.getDriveSettings().getRootDirectory().getOriginalFile());
    }

    @Override
    public void watchDirectory(File dir) {
        System.out.println("AndroidWatchdogListener.watchDirectory");
        if (!watchesRoot)
            try {
                watchesRoot = true;
                fileObserver = new FileObserver(dir.getAbsolutePath()) {
                    @Override
                    public void onEvent(int event, String path) {
                        if (path != null)
                            try {
                                path = meinDriveService.getDriveSettings().getRootDirectory().getOriginalFile().getAbsolutePath() + File.separator + path;
                                analyzeEvent(event, path);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            } finally {
                                ignoredSemaphore.release();
                            }
                    }
                };
                fileObserver.startWatching();
            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    private void analyzeEvent(final int event, String path) throws InterruptedException {
        if (FileObserver.CLOSE_WRITE == event
                || FileObserver.CREATE == event
                || FileObserver.DELETE == event
                || FileObserver.MOVED_TO == event
                || FileObserver.MOVED_FROM == event) {
            ignoredSemaphore.acquire();
            if (!ignoredMap.containsKey(path)) {
                System.out.println("AndroidWatchdogListener.analyzeEvent: " + path);
                watchDogTimer.start();
                pathCollection.addPath(path);
            }
        }
    }

    @Override
    public void foundDirectory(FsDirectory fsDirectory) {
        System.out.println("AndroidWatchdogListener.foundDirectory");
    }

    @Override
    public void run() {
        System.out.println("AndroidWatchdogListener.run.nothing to do :)");
        RWLock lock = new RWLock();
        lock.lockWrite();
        while (!Thread.currentThread().isInterrupted()) {
            lock.lockWrite();
        }
        System.out.println("AndroidWatchdogListener.run.stop");
        fileObserver.stopWatching();
    }

    @Override
    public void onTimerStopped() {
        super.onTimerStopped();
    }
}
