package de.mel.android.filesync.watchdog;

import android.os.FileObserver;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.mel.Lok;
import de.mel.auth.tools.N;
import de.mel.auth.tools.WatchDogTimer;
import de.mel.filesync.data.PathCollection;
import de.mel.filesync.index.watchdog.FileWatcher;
import de.mel.filesync.index.watchdog.UnixReferenceFileHandler;
import de.mel.filesync.service.MelFileSyncService;
import fun.with.promise.Promise;

/**
 * Watches directories on Android. {@link Watcher} is the Android component that delivers events for one folder but not its subfolders.
 * The {@link RecursiveWatcher} takes care of analyzing the events. It holds references to all {@link Watcher}s.
 * It deletes the references to the {@link Watcher}s once they are obsolete (folder or parent folder deleted) and creates new ones
 * for every new directory.
 * Created by xor on 31.07.2017.
 */
public class RecursiveWatcher extends FileWatcher {
    private final File target;
    private final Map<String, Watcher> watchers = new HashMap<>();
    private final File transferDirectory;
    private final UnixReferenceFileHandler unixReferenceFileHandler;

    public RecursiveWatcher(MelFileSyncService melFileSyncService) {
        super(melFileSyncService);
        this.target = melFileSyncService.getFileSyncSettings().getRootDirectory().getOriginalFile();
        watch(target);
        this.melFileSyncService = melFileSyncService;
        this.setStageIndexer(melFileSyncService.getStageIndexer());
        this.transferDirectory = melFileSyncService.getFileSyncSettings().getTransferDirectoryFile();
        this.transferDirectoryPath = transferDirectory.getAbsolutePath();
        this.watchDogTimer = new WatchDogTimer("recursive watcher",this::onTimerStopped, 15, 100, 1000);
        unixReferenceFileHandler = new UnixReferenceFileHandler(melFileSyncService.getServiceInstanceWorkingDirectory(), target, melFileSyncService.getFileSyncSettings().getTransferDirectory());
        unixReferenceFileHandler.onStart();
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + melFileSyncService.getRunnableName();
    }

    @Override
    public Promise<Void, Void> onShutDown() {
        for (Watcher watcher : watchers.values())
            watcher.stopWatching();
        return null;
    }

    @Override
    public void runImpl() {
        // nothing to do
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
            recursiveWatcher.onWatcherEvent(this, event, path);
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

    private Set<String> writePaths = new HashSet<>();

    private void onWatcherEvent(Watcher watcher, int event, String path) {
        File f = path != null ? new File(watcher.getTarget(), path) : watcher.getTarget();
        if (watcher.getTarget().getAbsolutePath().startsWith(transferDirectory.getAbsolutePath()))
            return;
        if ((FileObserver.CREATE & event) != 0 && f.exists() && f.isDirectory()) {
            watch(f);
        }
        try {
            String fPath = f.getAbsolutePath();
            if (checkEvent(event, FileObserver.DELETE_SELF)) {
//                Lok.warn("delete self" + positiveList);
                this.watchers.remove(f.getAbsolutePath());
                // folder was deleted. so check if we are still waiting for a writing event and remove it.
                Set<String> newWritePaths = new HashSet<>();
                for (String writePath : writePaths) {
                    if (writePath.startsWith(fPath))
                        newWritePaths.add(writePath);
                }
                writePaths = newWritePaths;
                pathCollection.addPath(f);
                startTimer();
                return;
            }
            if (checkEvent(event, FileObserver.MOVED_FROM)) {
                this.watchers.remove(f.getAbsolutePath());
                pathCollection.addPath(f);
                startTimer();
                return;
            }
            if (checkEvent(event, FileObserver.MOVED_TO)) {
                pathCollection.addPath(f);
                startTimer();
                return;
            }
            if (checkEvent(event, FileObserver.CLOSE_WRITE)) {
                writePaths.remove(fPath);
                startTimer();
                return;
            }
            if (checkEvent(event, FileObserver.OPEN)) {
                if (writePaths.contains(fPath)) {
                    stopTimer();
                }
                return;
            }
            if (checkEvent(event, FileObserver.MODIFY)) {
                writePaths.add(fPath);
                stopTimer();
                return;
            }
            if (checkEvent(event, FileObserver.DELETE)) {
                pathCollection.addPath(f);
                startTimer();
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopTimer() {
        N.r(() -> watchDogTimer.waite());
    }

    private void startTimer() {
        if (melFileSyncService.getMelAuthService().getPowerManager().heavyWorkAllowed())
            N.r(() -> watchDogTimer.start());
        else
            surpressEvent();
    }

    private boolean checkEvent(int event, int eventCode) {
        return (event & eventCode) != 0;
    }

    @Override
    public void onTimerStopped() {
        PathCollection pathCollection = new PathCollection();
        try {
            if (writePaths.size() > 0) {
                Lok.debug("assuming there is still writing in progess...");
                startTimer();
                return;
            }
            /**
             * we cannot retrieve all newly created things, so we have to do it now.
             * and watching the directories as well
             */
            Lok.debug("stopped");
            List<File> paths = unixReferenceFileHandler.stuffModifiedAfter();
            pathCollection.addAll(paths);
            for (File f : paths) {
                if (f.exists() && f.isDirectory()) {
                    watchDirectory(f);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        stageIndexer.examinePaths(this, pathCollection);
    }
}
