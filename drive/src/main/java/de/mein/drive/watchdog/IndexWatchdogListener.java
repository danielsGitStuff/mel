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

    private final String name;
    private WatchDogTimer watchDogTimer = new WatchDogTimer(this, 20, 100, 100);
    private MeinDriveService meinDriveService;
    private PathCollection pathCollection = new PathCollection();
    private Map<String, String> ignoredMap = new ConcurrentHashMap<>();
    private Semaphore ignoredSemaphore = new Semaphore(1, true);
    private String workingDirectoryPath;


    public static IndexWatchdogListener runInstance(MeinDriveService meinDriveService) {
        WatchService watchService = null;
        IndexWatchdogListener watchdogListener = null;
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            System.out.println("WatchDog.windows");
            watchdogListener = new IndexWatchDogWindows(watchService);
        } else {
            System.out.println("WatchDog.unix");
            watchdogListener = new IndexWatchdogUnix(watchService);
        }
        watchdogListener.meinDriveService = meinDriveService;
        watchdogListener.adjustExecutor();
        watchdogListener.executorService.submit(watchdogListener);
        return watchdogListener;
    }

    @Override
    public void onTimerStopped() {
        System.out.println("IndexWatchdogListener.onTimerStopped");
        meinDriveService.addJob(new FsSyncJob(pathCollection));
        pathCollection = new PathCollection();
    }

    protected WatchService watchService;
    protected WatchEvent.Kind<?>[] KINDS = new WatchEvent.Kind<?>[]{StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE};

    public IndexWatchdogListener(String name, WatchService watchService) {
        this.name = name;
        this.watchService = watchService;
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

    @Override
    public void run() {
        try {
            Thread.currentThread().setName(name);
            while (true) {
                WatchKey watchKey = watchService.take();
                List<WatchEvent<?>> events = watchKey.pollEvents();
                for (WatchEvent<?> event : events) {
                    Path eventPath = (Path) event.context();
                    Path watchKeyPath = (Path) watchKey.watchable();
                    String objectPath = watchKeyPath.toString() + File.separator + eventPath.toString();
                    ignoredSemaphore.acquire();
                    if (!ignoredMap.containsKey(objectPath)) {
                        System.out.println("IndexWatchdogListener[" + meinDriveService.getDriveSettings().getRole() + "].got event for: " + objectPath);
                        File object = new File(objectPath);
                        analyze(event, object);
                    } else {
                        ignoredMap.remove(objectPath);
                    }
                    ignoredSemaphore.release();
                }
                // reset the key
                boolean valid = watchKey.reset();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
