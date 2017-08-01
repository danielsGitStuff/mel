package de.mein.android.drive.watchdog;

import android.os.FileObserver;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import de.mein.auth.tools.WaitLock;
import de.mein.drive.index.watchdog.IndexWatchdogListener;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.sql.FsDirectory;

/**
 * Created by xor on 31.07.2017.
 */

public class RecursiveWatcher extends IndexWatchdogListener {
    private final File target;
    private final Map<String, Watcher> watchers = new HashMap<>();
    private final MeinDriveService meinDriveService;

    public RecursiveWatcher(MeinDriveService meinDriveService) {
        this.target = meinDriveService.getDriveSettings().getRootDirectory().getOriginalFile();
        watch(target);
        this.meinDriveService = meinDriveService;
        this.setStageIndexer(meinDriveService.getStageIndexer());
        this.transferDirectoryPath = meinDriveService.getDriveSettings().getTransferDirectoryPath();
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + meinDriveService.getRunnableName();
    }

    @Override
    public void foundDirectory(FsDirectory fsDirectory) {
        watch(fsDirectory.getOriginal());
    }

    @Override
    public void onShutDown() {
        for (Watcher watcher : watchers.values())
            watcher.stopWatching();
    }

    @Override
    public void runImpl() {
        //nothing to do
    }

    @Override
    public void watchDirectory(File dir) {
        watch(dir);
    }

    private class Watcher extends FileObserver {

        private final RecursiveWatcher recursiveWatcher;
        private final File target;

        public Watcher(RecursiveWatcher recursiveWatcher, File target) {
            super(target.getAbsolutePath());
            this.target = target;
            this.recursiveWatcher = recursiveWatcher;
        }

        @Override
        public void onEvent(int event, @Nullable String path) {
            recursiveWatcher.eve(this, event, path);
        }

        public File getTarget() {
            return target;
        }
    }

    private void watch(File target) {
        if (!watchers.containsKey(target.getAbsolutePath())) {
            Watcher watcher = new Watcher(this, target);
            watchers.put(target.getAbsolutePath(), watcher);
            watcher.startWatching();
        }
    }

    private void eve(Watcher watcher, int event, String path) {
        File f = path != null ? new File(watcher.getTarget() + File.separator + path) : watcher.getTarget();
        if ((FileObserver.CREATE & event) != 0 && f.exists() && f.isDirectory()) {
            watch(f);
        }
        try {
            if ((FileObserver.ACCESS & event) == 0) {
                watchDogTimer.start();
            } else analyze(event, watcher, path);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void analyze(int event, Watcher watcher, String path) {
        try {
            watchDogTimer.start();
            File file = new File(watcher.getTarget().getAbsoluteFile() + File.separator + path);
            if ((event & FileObserver.MODIFY) != 0 || (event & FileObserver.MOVE_SELF) != 0) {
                // figure out whether or not writing to the file is still in progress
                try {
                    double r = Math.random();
                    System.out.println("IndexWatchdogListener.analyze.attempt to open " + file.getAbsolutePath() + " " + r);
                    InputStream is = new FileInputStream(file);
                    is.close();
                    System.out.println("IndexWatchdogListener.analyze.success " + r);
                    watchDogTimer.resume();
                } catch (FileNotFoundException e) {
                    System.out.println("IndexWatchdogListener.analyze.file not found: " + file.getAbsolutePath());
                } catch (Exception e) {
                    System.out.println("IndexWatchdogListener.analyze.writing in progress");
                    watchDogTimer.waite();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}