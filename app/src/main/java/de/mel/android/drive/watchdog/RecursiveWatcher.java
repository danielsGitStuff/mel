package de.mel.android.drive.watchdog;

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
import de.mel.auth.file.AFile;
import de.mel.auth.tools.WatchDogTimer;
import de.mel.drive.data.PathCollection;
import de.mel.drive.index.watchdog.IndexWatchdogListener;
import de.mel.drive.index.watchdog.UnixReferenceFileHandler;
import de.mel.drive.service.MelFileSyncService;
import org.jdeferred.Promise;

/**
 * Watches directories on Android. {@link Watcher} is the Android component that delivers events for one folder but not its subfolders.
 * The {@link RecursiveWatcher} takes care of analyzing the events. It holds references to all {@link Watcher}s.
 * It deletes the references to the {@link Watcher}s once they are obsolete (folder or parent folder deleted) and creates new ones
 * for every new directory.
 * Created by xor on 31.07.2017.
 */
public class RecursiveWatcher extends IndexWatchdogListener {
    private final AFile target;
    private final Map<String, Watcher> watchers = new HashMap<>();
    private final AFile transferDirectory;
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
    public Promise<Void, Void, Void> onShutDown() {
        for (Watcher watcher : watchers.values())
            watcher.stopWatching();
        return null;
    }

    @Override
    public void runImpl() {
        // nothing to do
    }

    @Override
    public void watchDirectory(AFile dir) {
        watch(dir);
    }


    private class Watcher extends FileObserver {

        private final RecursiveWatcher recursiveWatcher;
        private final AFile target;

        public Watcher(RecursiveWatcher recursiveWatcher, AFile target) {
            super(target.getAbsolutePath());
            this.target = target;
            this.recursiveWatcher = recursiveWatcher;
        }

        @Override
        public void onEvent(int event, @Nullable String path) {
            recursiveWatcher.onWatcherEvent(this, event, path);
        }

        public AFile getTarget() {
            return target;
        }
    }

    private void watch(AFile target) {
        if (!watchers.containsKey(target.getAbsolutePath())) {
            Watcher watcher = new Watcher(this, target);
            watchers.put(target.getAbsolutePath(), watcher);
            watcher.startWatching();
        }
    }

    private Set<String> writePaths = new HashSet<>();

    private void onWatcherEvent(Watcher watcher, int event, String path) {
        AFile f = path != null ? AFile.instance(watcher.getTarget().getAbsolutePath() + File.separator + path) : watcher.getTarget();
        if (transferDirectory.hasSubContent(watcher.getTarget()))
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
                startTimer();
            } else if (checkEvent(event, FileObserver.CLOSE_WRITE)) {
//                Lok.warn("close.write: " + positiveList);
                writePaths.remove(f.getAbsolutePath());
                startTimer();
            } else if (checkEvent(event,
                    FileObserver.DELETE,
                    FileObserver.DELETE_SELF,
                    FileObserver.CREATE,
                    FileObserver.MOVE_SELF,
                    FileObserver.MOVED_FROM,
                    FileObserver.MOVED_TO)) {
//                Lok.warn("1st: " + positiveList);
                startTimer();
            } else if (checkEvent(event, FileObserver.MODIFY)) {
//                Lok.warn("modify: " + positiveList);
                writePaths.add(f.getAbsolutePath());
                startTimer();
            } else {
//                Lok.warn("something else: " + positiveList);
            }
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

    /**
     * starts the timer if allowed.
     * @throws InterruptedException
     */
    private void startTimer() throws InterruptedException {
        if (melFileSyncService.getMelAuthService().getPowerManager().heavyWorkAllowed()) {
            watchDogTimer.start();
        } else {
            surpressEvent();
        }
    }

    private boolean hasWrittenSinceStart = false;


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
            List<AFile<?>> paths = unixReferenceFileHandler.stuffModifiedAfter();
            pathCollection.addAll(paths);
            for (AFile f : paths) {
                if (f.exists() && f.isDirectory()) {
                    watchDirectory(f);
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        stageIndexer.examinePaths(this, pathCollection);
    }
}