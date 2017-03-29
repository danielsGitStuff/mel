package de.mein.android.drive.watchdog;

import android.os.FileObserver;

import java.io.File;

import de.mein.drive.service.MeinDriveService;
import de.mein.drive.sql.FsDirectory;
import de.mein.drive.watchdog.IndexWatchdogListener;
import de.mein.sql.RWLock;

/**
 * Created by xor on 2/6/17.
 */

public class AndroidWatchdogListener extends IndexWatchdogListener {
    private boolean watchesRoot = false;
    private FileObserver fileObserver;

    public AndroidWatchdogListener(MeinDriveService meinDriveService) {
        this.meinDriveService = meinDriveService;
        this.setStageIndexer(meinDriveService.getStageIndexer());
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
                        if (path == null)
                            System.out.println("AndroidWatchdogListener.onEvent");
                        System.out.println("AndroidWatchdogListener.onEvent: " + path);
                        if (path != null)
                            try {
                                ignoredSemaphore.acquire();
                                if (!ignoredMap.containsKey(path))
                                    analyzeFile(new File(path));

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

    private void analyzeFile(File file) {
        System.out.println("AndroidWatchdogListener.analyzeFile: " + file.getAbsolutePath());
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
}
