package de.mel.drive.index.watchdog;

import de.mel.Lok;
import de.mel.auth.tools.BackgroundExecutor;
import de.mel.drive.data.PathCollection;
import de.mel.drive.sql.DriveDatabaseManager;
import de.mel.sql.SqlQueriesException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Created by xor on 11/24/16.
 */
public class StageIndexer extends BackgroundExecutor {


    @Override
    protected ExecutorService createExecutorService(ThreadFactory threadFactory) {
        return Executors.newSingleThreadExecutor(threadFactory);
    }

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


    public void examinePaths(IndexWatchdogListener indexWatchdogListener, PathCollection pathCollection) {
        StageIndexerRunnable indexerRunnable = new StageIndexerRunnable(databaseManager, pathCollection, indexWatchdogListener);
        indexerRunnable.setStagingDoneListener(stagingDoneListener);
        Lok.debug(getClass().getSimpleName() + ".examinePaths.execute.on: " + Thread.currentThread().getName());
        execute(indexerRunnable);
        Lok.debug(getClass().getSimpleName() + ".examinePaths.executed.on: " + Thread.currentThread().getName());
    }
}
