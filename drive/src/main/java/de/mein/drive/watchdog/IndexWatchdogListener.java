package de.mein.drive.watchdog;

import com.sun.nio.file.ExtendedWatchEventModifier;

import de.mein.drive.data.PathCollection;
import de.mein.drive.index.BackgroundExecutor;
import de.mein.drive.index.ICrawlerListener;
import de.mein.drive.jobs.FsSyncJob;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.sql.FsFile;
import de.mein.drive.watchdog.timer.WatchDogTimer;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Created by xor on 7/11/16.
 */
@SuppressWarnings("Duplicates")
public abstract class IndexWatchdogListener extends BackgroundExecutor implements ICrawlerListener, Runnable, WatchDogTimer.WatchDogTimerFinished {

    protected String name;
    protected WatchDogTimer watchDogTimer = new WatchDogTimer(this, 20, 100, 100);
    protected MeinDriveService meinDriveService;
    protected PathCollection pathCollection = new PathCollection();
    protected Map<String, String> ignoredMap = new ConcurrentHashMap<>();
    protected Semaphore ignoredSemaphore = new Semaphore(1, true);
    protected String workingDirectoryPath;
    private static WatchDogRunner watchDogRunner = meinDriveService1 -> {
        WatchService watchService1 = null;
        IndexWatchdogListener watchdogListener = null;
        try {
            watchService1 = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            System.out.println("WatchDog.windows");
            watchdogListener = new IndexWatchDogWindows(watchService1);
        } else {
            System.out.println("WatchDog.unix");
            watchdogListener = new IndexWatchdogUnix(watchService1);
        }
        watchdogListener.meinDriveService = meinDriveService1;
        watchdogListener.adjustExecutor();
        watchdogListener.executorService.submit(watchdogListener);
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
        meinDriveService.addJob(new FsSyncJob(pathCollection));
        pathCollection = new PathCollection();
    }


    public IndexWatchdogListener(){

    }

    @Override
    public void foundFile(FsFile fsFile) {

    }

    public abstract void watchDirectory(File dir);


    @Override
    public void done() {
        System.out.println("IndexWatchdogListener.done");
        meinDriveService.start();
    }




    public void analyze(WatchEvent<?> event, File file) {
        try {
            watchDogTimer.start();
            if (event.kind().equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
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
            } else if (event.kind().equals(StandardWatchEventKinds.ENTRY_CREATE) && file.exists() && file.isDirectory()) {
                this.watchDirectory(file);
            }
            System.out.println("IndexWatchdogListener[" + meinDriveService.getDriveSettings().getRole() + "].analyze[" + event.kind() + "]: " + file.getAbsolutePath());
            pathCollection.addPath(file.getAbsolutePath());
            if (event.kind().equals(ExtendedWatchEventModifier.FILE_TREE)) {
                System.out.println("ALARM!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void ignore(String path) throws InterruptedException {
        System.out.println("IndexWatchdogListener[" + meinDriveService.getDriveSettings().getDriveDetails().getRole()
                + "].ignore(" + path + ")");
        ignoredSemaphore.acquire();
        ignoredMap.put(path, path);
        ignoredSemaphore.release();
    }

    public IndexWatchdogListener setWorkingDirectoryPath(String workingDirectoryPath) {
        this.workingDirectoryPath = workingDirectoryPath;
        return this;
    }

    public void stopIgnore(String path) {
        System.out.println("IndexWatchdogListener.stopIgnore");
        ignoredMap.remove(path);
    }
}
