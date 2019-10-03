package de.mel.drive.index.watchdog;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.tools.lock.Warden;
import de.mel.drive.data.PathCollection;
import de.mel.drive.index.IndexListener;
import de.mel.auth.file.AFile;
import de.mel.drive.service.MelFileSyncService;
import de.mel.auth.tools.WatchDogTimer;
import org.jdeferred.Promise;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by xor on 7/11/16.
 */
@SuppressWarnings("Duplicates")
public abstract class IndexWatchdogListener extends DeferredRunnable implements IndexListener, Runnable, WatchDogTimer.WatchDogTimerFinished {

    private static WatchDogRunner watchDogRunner = melDriveService1 -> {
        WatchService watchService1 = null;
        IndexWatchdogListener watchdogListener;
        try {
            watchService1 = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            Lok.debug("WatchDog.windows");
            watchdogListener = new IndexWatchDogListenerWindows(melDriveService1, watchService1);
        } else {
            Lok.debug("WatchDog.unix");
            watchdogListener = new IndexWatchdogListenerUnix(melDriveService1, watchService1);
        }
        watchdogListener.melFileSyncService = melDriveService1;
        watchdogListener.melFileSyncService.execute(watchdogListener);
        return watchdogListener;
    };
    protected String name;
    protected WatchDogTimer watchDogTimer = new WatchDogTimer("Indexer",this, 20, 100, 150);
    protected MelFileSyncService melFileSyncService;
    protected PathCollection pathCollection = new PathCollection();
    protected Map<String, Integer> ignoredMap = new ConcurrentHashMap<>();
    protected Semaphore ignoredSemaphore = new Semaphore(1, true);
    protected String transferDirectoryPath;
    protected StageIndexer stageIndexer;
    private ReentrantLock surpressLock = new ReentrantLock();
    private boolean hasSupressedEvents = false;


    public IndexWatchdogListener(MelFileSyncService melFileSyncService) {
        this.melFileSyncService = melFileSyncService;
    }

    public static void setWatchDogRunner(WatchDogRunner watchDogRunner) {
        IndexWatchdogListener.watchDogRunner = watchDogRunner;
    }

    public static IndexWatchdogListener runInstance(MelFileSyncService melFileSyncService) {
        return IndexWatchdogListener.watchDogRunner.runInstance(melFileSyncService);
    }

    @Override
    public void onTimerStopped() {
        Lok.debug("IndexWatchdogListener.onTimerStopped");
        //melDriveService.addJob(new FsSyncJob(pathCollection));
        stageIndexer.examinePaths(this, pathCollection);
        pathCollection = new PathCollection();
    }

    public StageIndexer getStageIndexer() {
        return stageIndexer;
    }

    public void setStageIndexer(StageIndexer stageIndexer) {
        this.stageIndexer = stageIndexer;
    }

    public abstract void watchDirectory(AFile dir) throws IOException;

    @Override
    public void done(Long stageSetId, Warden warden) {
        Lok.debug("IndexWatchdogListener.done");
    }

    public void ignore(String path, int amount) throws InterruptedException {
        ignoredSemaphore.acquire();
        if (ignoredMap.containsKey(path))
            amount += ignoredMap.get(path);
        ignoredMap.put(path, amount);
        ignoredSemaphore.release();
    }

    public IndexWatchdogListener setTransferDirectoryPath(String transferDirectoryPath) {
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
    public Promise<Void, Void, Void> onShutDown() {
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
        IndexWatchdogListener runInstance(MelFileSyncService melFileSyncService);
    }
}
