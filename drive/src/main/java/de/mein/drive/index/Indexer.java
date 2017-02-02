package de.mein.drive.index;

import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.service.SyncHandler;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.FsDirectory;
import de.mein.drive.watchdog.IndexWatchdogListener;
import de.mein.sql.SqlQueriesException;

import java.io.File;

/**
 * Created by xor on 10.07.2016.
 */
public class Indexer extends BackgroundExecutor {
    private IndexerRunnable crawlerRunnable;

    public Indexer(DriveDatabaseManager databaseManager, IndexWatchdogListener indexWatchdogListener, ICrawlerListener... listeners) throws SqlQueriesException {
        crawlerRunnable = new IndexerRunnable(databaseManager, indexWatchdogListener, listeners);
    }

    public void setSyncHandler(SyncHandler syncHandler) {
        crawlerRunnable.setSyncHandler(syncHandler);
    }

    public void ignorePath(String path)  {
        //todo escalate?
        try {
            crawlerRunnable.getIndexWatchdogListener().ignore(path);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void stopIgnore(String path) {
        crawlerRunnable.getIndexWatchdogListener().stopIgnore(path);
    }

    public void start() {
        adjustExecutor();
        executorService.submit(crawlerRunnable);
    }

    public void watchFsDirectory(FsDirectory fsDirectory) {
        crawlerRunnable.getIndexWatchdogListener().foundDirectory(fsDirectory);
    }

    public void watchDirectory(File dir) {
        crawlerRunnable.getIndexWatchdogListener().watchDirectory(dir);
    }

    public RootDirectory getRootDirectory() {
        return crawlerRunnable.getRootDirectory();
    }
}
