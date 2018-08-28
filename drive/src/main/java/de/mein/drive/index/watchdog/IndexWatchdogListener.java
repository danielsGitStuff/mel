package de.mein.drive.index.watchdog;

import de.mein.DeferredRunnable;
import de.mein.auth.service.power.PowerManager;
import de.mein.drive.data.PathCollection;
import de.mein.drive.index.IndexListener;
import de.mein.auth.file.AFile;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.sql.FsFile;
import de.mein.auth.tools.WatchDogTimer;

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
public abstract class IndexWatchdogListener extends DeferredRunnable implements IndexListener, Runnable, WatchDogTimer.WatchDogTimerFinished, PowerManager.PowerManagerListener {

    protected String name;
    protected WatchDogTimer watchDogTimer = new WatchDogTimer(this, 20, 100, 100);
    protected MeinDriveService meinDriveService;
    protected PathCollection pathCollection = new PathCollection();
    protected Map<String, Integer> ignoredMap = new ConcurrentHashMap<>();
    protected Semaphore ignoredSemaphore = new Semaphore(1, true);
    protected String transferDirectoryPath;
    protected StageIndexer stageIndexer;
    private ReentrantLock surpressLock = new ReentrantLock();
    private boolean hasSupressedEvents = false;

    private static WatchDogRunner watchDogRunner = meinDriveService1 -> {
        WatchService watchService1 = null;
        IndexWatchdogListener watchdogListener;
        try {
            watchService1 = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            System.out.println("WatchDog.windows");
            watchdogListener = new IndexWatchDogListenerWindows(meinDriveService1, watchService1);
        } else {
            System.out.println("WatchDog.unix");
            watchdogListener = new IndexWatchdogListenerUnix2(meinDriveService1, watchService1);
        }
        watchdogListener.meinDriveService = meinDriveService1;
        watchdogListener.meinDriveService.execute(watchdogListener);
        return watchdogListener;
    };

    public static void setWatchDogRunner(WatchDogRunner watchDogRunner) {
        IndexWatchdogListener.watchDogRunner = watchDogRunner;
    }

    public interface WatchDogRunner {
        IndexWatchdogListener runInstance(MeinDriveService meinDriveService);
    }

    public static IndexWatchdogListener runInstance(MeinDriveService meinDriveService) {
        return IndexWatchdogListener.watchDogRunner.runInstance(meinDriveService);
    }

    @Override
    public void onTimerStopped() {
        System.out.println("IndexWatchdogListener.onTimerStopped");
        //meinDriveService.addJob(new FsSyncJob(pathCollection));
        stageIndexer.examinePaths(this,pathCollection);
        pathCollection = new PathCollection();
    }


    public IndexWatchdogListener(MeinDriveService meinDriveService) {
        this.meinDriveService = meinDriveService;
        this.meinDriveService.getMeinAuthService().getPowerManager().addPowerListener(this);
    }

    public StageIndexer getStageIndexer() {
        return stageIndexer;
    }

    public void setStageIndexer(StageIndexer stageIndexer) {
        this.stageIndexer = stageIndexer;
    }

    @Override
    public void foundFile(FsFile fsFile) {

    }

    public abstract void watchDirectory(AFile dir);


    @Override
    public void done(Long stageSetId) {
        System.out.println("IndexWatchdogListener.done");
    }


    public void ignore(String path, int amount) throws InterruptedException {
        System.out.println("IndexWatchdogListener[" + meinDriveService.getDriveSettings().getDriveDetails().getRole()
                + "].ignore(" + path + ")");
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
        System.out.println("IndexWatchdogListener[" + meinDriveService.getDriveSettings().getDriveDetails().getRole()
                + "].stopignore(" + path + ")");
        ignoredMap.remove(path);
        ignoredSemaphore.release();
    }

    @Override
    public void shutDown() {
        super.shutDown();
        watchDogTimer.cancel();
        // dereference cause JavaDoc says so
        watchDogTimer = null;
    }

    protected void surpressEvent() {
        surpressLock.lock();
        hasSupressedEvents = true;
        surpressLock.unlock();
    }

    @Override
    public void onHeavyWorkAllowed() {
        surpressLock.lock();
        try {
            if (hasSupressedEvents) {
                hasSupressedEvents = false;
                watchDogTimer.start();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            surpressLock.unlock();
        }
    }
}
