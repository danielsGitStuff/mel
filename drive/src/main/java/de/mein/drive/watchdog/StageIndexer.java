package de.mein.drive.watchdog;

import de.mein.drive.data.PathCollection;
import de.mein.drive.index.BackgroundExecutor;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.sql.SqlQueriesException;

import java.util.concurrent.Executors;

/**
 * Created by xor on 11/24/16.
 */
public class StageIndexer extends BackgroundExecutor {

    public interface StagingDoneListener {
        void onStagingFsEventsDone(Long stageSetId) throws InterruptedException, SqlQueriesException;
    }

    public interface StagingFailedListener {

    }

    private StagingDoneListener stagingDoneListener;

    public void setStagingDoneListener(StagingDoneListener stagingDoneListener) {
        this.stagingDoneListener = stagingDoneListener;
    }

    private final DriveDatabaseManager databaseManager;

    public StageIndexer(DriveDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }


    public void examinePaths(PathCollection pathCollection) {
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor();
        }
        StageIndexerRunnable indexerRunnable = new StageIndexerRunnable(databaseManager, pathCollection);
        indexerRunnable.setStagingDoneListener(stagingDoneListener);
        executorService.submit(indexerRunnable);
    }
}