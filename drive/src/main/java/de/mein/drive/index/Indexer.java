package de.mein.drive.index;

import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.service.sync.SyncHandler;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.FsDirectory;
import de.mein.drive.watchdog.IndexWatchdogListener;
import de.mein.sql.SqlQueriesException;

import java.io.File;

/**
 * Created by xor on 10.07.2016.
 */
public class Indexer  {
    private final MeinDriveService meinDriveService;
    private IndexerRunnable crawlerRunnable;

    public Indexer(DriveDatabaseManager databaseManager, IndexWatchdogListener indexWatchdogListener, ICrawlerListener... listeners) throws SqlQueriesException {
        meinDriveService = databaseManager.getMeinDriveService();
        crawlerRunnable = new IndexerRunnable(databaseManager, indexWatchdogListener, listeners);
    }

    public void setSyncHandler(SyncHandler syncHandler) {
        crawlerRunnable.setSyncHandler(syncHandler);
    }

    public void ignorePath(String path, int amount) {
        //todo escalate?
        try {
            crawlerRunnable.getIndexWatchdogListener().ignore(path, amount);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void stopIgnore(String path) throws InterruptedException {
        crawlerRunnable.getIndexWatchdogListener().stopIgnore(path);
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


    public void shutDown(){
        crawlerRunnable.shutDown();
    }

    public void start(){
        meinDriveService.execute(crawlerRunnable);
    }


}
