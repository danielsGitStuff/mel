package de.mein.drive.index;

import de.mein.DeferredRunnable;
import de.mein.auth.file.AFile;
import de.mein.drive.data.fs.RootDirectory;
import de.mein.drive.service.MeinDriveService;
import de.mein.drive.service.sync.SyncHandler;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.FsDirectory;
import de.mein.drive.index.watchdog.IndexWatchdogListener;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.impl.DeferredObject;

import java.io.File;

/**
 * Created by xor on 10.07.2016.
 */
public class Indexer  {
    private final MeinDriveService meinDriveService;
    private IndexerRunnable indexerRunnable;

    public Indexer(DriveDatabaseManager databaseManager, IndexWatchdogListener indexWatchdogListener, IndexListener... listeners) throws SqlQueriesException {
        meinDriveService = databaseManager.getMeinDriveService();
        indexerRunnable = new IndexerRunnable(databaseManager, indexWatchdogListener, listeners);
    }

    public void setSyncHandler(SyncHandler syncHandler) {
        indexerRunnable.setSyncHandler(syncHandler);
    }

    public void ignorePath(String path, int amount) {
        //todo escalate?
        try {
            indexerRunnable.getIndexWatchdogListener().ignore(path, amount);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void stopIgnore(String path) throws InterruptedException {
        indexerRunnable.getIndexWatchdogListener().stopIgnore(path);
    }

    public void watchFsDirectory(FsDirectory fsDirectory) {
        indexerRunnable.getIndexWatchdogListener().foundDirectory(fsDirectory);
    }

    public void watchDirectory(AFile dir) {
        indexerRunnable.getIndexWatchdogListener().watchDirectory(dir);
    }

    public RootDirectory getRootDirectory() {
        return indexerRunnable.getRootDirectory();
    }


    public void shutDown(){
        indexerRunnable.shutDown();
    }

    public DeferredObject<DeferredRunnable, Exception, Void> start(){
        meinDriveService.execute(indexerRunnable);
        return indexerRunnable.getStartedDeferred();
    }

    public DeferredObject<DeferredRunnable, Exception, Void> getIndexerStartedDeferred(){
        return indexerRunnable.getStartedDeferred();
    }


}
