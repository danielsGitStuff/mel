package de.mel.filesync.index.watchdog;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.file.IFile;
import de.mel.auth.tools.lock2.BunchOfLocks;
import de.mel.filesync.data.PathCollection;
import de.mel.filesync.index.IndexListener;
import de.mel.filesync.service.MelFileSyncService;
import de.mel.auth.tools.WatchDogTimer;
import fun.with.promise.Promise;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by xor on 7/11/16.
 */
@SuppressWarnings("Duplicates")
public abstract class FileWatcher extends DeferredRunnable implements IndexListener, Runnable, WatchDogTimer.WatchDogTimerFinished {

    /**
     * How long the files must be quiet before the changes are indexed: 100 ms, then this many periods without a change (about 3 s).
     * The app keeps the default (big copies take a while); tests make it short. Read when a watcher is made.
     */
    public static volatile int quietRepetitions = 20;
    public static volatile int quietPeriodMs = 150;

    protected String name;
    protected WatchDogTimer watchDogTimer = new WatchDogTimer("Indexer", this, quietRepetitions, 100, quietPeriodMs);
    protected MelFileSyncService melFileSyncService;
    protected PathCollection pathCollection = new PathCollection();
    protected Map<String, Integer> ignoredMap = new ConcurrentHashMap<>();
    protected Semaphore ignoredSemaphore = new Semaphore(1, true);
    protected String transferDirectoryPath;
    protected StageIndexer stageIndexer;
    private ReentrantLock surpressLock = new ReentrantLock();
    private boolean hasSupressedEvents = false;


    public FileWatcher(MelFileSyncService melFileSyncService) {
        this.melFileSyncService = melFileSyncService;
    }

    @Override
    public void onTimerStopped() {
        Lok.debug("IndexWatchdogListener.onTimerStopped");
        //melDriveService.addJob(new FsSyncJob(pathCollection));
        stageIndexer.examinePaths(this, takePathCollection());
    }

    /**
     * @return what the events reported since the last time. The watcher starts over with an empty one.
     */
    protected synchronized PathCollection takePathCollection() {
        PathCollection taken = pathCollection;
        pathCollection = new PathCollection();
        return taken;
    }

    protected synchronized void addEventPath(IFile file) {
        pathCollection.addPath(file);
    }

    public StageIndexer getStageIndexer() {
        return stageIndexer;
    }

    public void setStageIndexer(StageIndexer stageIndexer) {
        this.stageIndexer = stageIndexer;
    }

    public abstract void watchDirectory(IFile dir) throws IOException;

    @Override
    public void done(Long stageSetId, BunchOfLocks bunchOfLocks) {
        Lok.debug("IndexWatchdogListener.done");
    }

    public void ignore(String path, int amount) throws InterruptedException {
        ignoredSemaphore.acquire();
        if (ignoredMap.containsKey(path))
            amount += ignoredMap.get(path);
        ignoredMap.put(path, amount);
        ignoredSemaphore.release();
    }

    public FileWatcher setTransferDirectoryPath(String transferDirectoryPath) {
        this.transferDirectoryPath = transferDirectoryPath;
        return this;
    }

    public void stopIgnore(String path) throws InterruptedException {
        ignoredSemaphore.acquire();
        Lok.debug("IndexWatchdogListener[" + melFileSyncService.getFileSyncSettings().getDriveDetails().getRole()
                + "].stopignore(" + path + ")");
        ignoredMap.remove(path);
        ignoredSemaphore.release();
    }

    @Override
    public Promise<Void, Void> onShutDown() {
        watchDogTimer.cancel();
        // dereference cause JavaDoc says so
        watchDogTimer = null;
        return null;
    }

    public WatchDogTimer getWatchDogTimer() {
        return watchDogTimer;
    }

    protected void surpressEvent() {
        surpressLock.lock();
        hasSupressedEvents = true;
        surpressLock.unlock();
    }


    public interface WatchDogRunner {
        FileWatcher runInstance(MelFileSyncService melFileSyncService);
    }
}
