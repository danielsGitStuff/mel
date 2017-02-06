package de.mein.drive.watchdog;

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
                        System.out.println("AndroidWatchdogListener.onEvent");
                    }
                };
                fileObserver.startWatching();
            } catch (Exception e) {
                e.printStackTrace();
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
}
