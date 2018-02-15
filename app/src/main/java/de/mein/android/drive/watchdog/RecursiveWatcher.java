package de.mein.android.drive.watchdog;

import android.os.FileObserver;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import de.mein.auth.tools.WaitLock;
import de.mein.drive.bash.BashTools;
import de.mein.drive.data.PathCollection;
import de.mein.drive.index.watchdog.IndexWatchdogListener;
import de.mein.drive.index.watchdog.UnixReferenceFileHandler;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.sql.FsDirectory;

/**
 * Created by xor on 31.07.2017.
 */

public class RecursiveWatcher extends IndexWatchdogListener {
    private final File target;
    private final Map<String, Watcher> watchers = new HashMap<>();
    private final File transferDirectory;
    private final UnixReferenceFileHandler unixReferenceFileHandler;

    public RecursiveWatcher(MeinDriveService meinDriveService) {
        super(meinDriveService);
        this.target = meinDriveService.getDriveSettings().getRootDirectory().getOriginalFile();
        watch(target);
        this.meinDriveService = meinDriveService;
        this.setStageIndexer(meinDriveService.getStageIndexer());
        this.transferDirectory = meinDriveService.getDriveSettings().getTransferDirectoryFile();
        this.transferDirectoryPath = transferDirectory.getAbsolutePath();
        unixReferenceFileHandler = new UnixReferenceFileHandler(meinDriveService.getServiceInstanceWorkingDirectory(), target, new File(meinDriveService.getDriveSettings().getTransferDirectoryPath()));
        unixReferenceFileHandler.onStart();
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
        // nothing to do
    }

    @Override
    public void watchDirectory(File dir) {
        watch(dir);
    }

    @Override
    public void onHeavyWorkForbidden() {

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
        if (watcher.getTarget().equals(transferDirectory))
            return;
        if ((FileObserver.CREATE & event) != 0 && f.exists() && f.isDirectory()) {
            watch(f);
        }
        try {
            //todo debug
            Map<String, Boolean> flags = flags(event);
            if (checkEvent(event,
                    FileObserver.CLOSE_WRITE,
                    FileObserver.DELETE,
                    FileObserver.DELETE_SELF,
                    FileObserver.CREATE,
                    FileObserver.MOVE_SELF,
                    FileObserver.MOVED_FROM,
                    FileObserver.MOVED_TO)) {
                startTimer();
            } else if (checkEvent(event, FileObserver.MODIFY))
                analyze(event, watcher, path);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean checkEvent(int event, int... expected) {
        for (int e : expected) {
            if ((e & event) != 0)
                return true;
        }
        return false;
    }

    private Map<String, Boolean> flags(int event) {
        Map<String, Boolean> flags = new HashMap<>();
        flags.put("access", (FileObserver.ACCESS & event) != 0);
        flags.put("all", (FileObserver.ALL_EVENTS & event) != 0);
        flags.put("attrib", (FileObserver.ATTRIB & event) != 0);
        flags.put("close.nowrite", (FileObserver.CLOSE_NOWRITE & event) != 0);
        flags.put("close.write", (FileObserver.CLOSE_WRITE & event) != 0);
        flags.put("create", (FileObserver.CREATE & event) != 0);
        flags.put("delete", (FileObserver.DELETE & event) != 0);
        flags.put("delete.self", (FileObserver.DELETE_SELF & event) != 0);
        flags.put("modify", (FileObserver.MODIFY & event) != 0);
        flags.put("modify.self", (FileObserver.MOVE_SELF & event) != 0);
        flags.put("moved.from", (FileObserver.MOVED_FROM & event) != 0);
        flags.put("moved.to", (FileObserver.MOVED_TO & event) != 0);
        flags.put("open", (FileObserver.OPEN & event) != 0);
        return flags;
    }

    private void startTimer() throws InterruptedException {
        if (meinDriveService.getMeinAuthService().getPowerManager().heavyWorkAllowed()) {
            watchDogTimer.start();
        } else {
            surpressEvent();
        }
    }

    public void analyze(int event, Watcher watcher, String path) {
        try {
            startTimer();
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

    @Override
    public void onTimerStopped() {
        PathCollection pathCollection = new PathCollection();
        try {
            /**
             * we cannot retrieve all newly created things, so we have to do it now.
             * and watching the directories as well
             */
            List<String> paths = unixReferenceFileHandler.stuffModifiedAfter();
            pathCollection.addAll(paths);
            for (String p : paths) {
                File f = new File(p);
                if (f.exists() && f.isDirectory()) {
                    watchDirectory(f);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        stageIndexer.examinePaths(this, pathCollection);
    }
}