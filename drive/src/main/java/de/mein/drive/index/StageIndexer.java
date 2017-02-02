package de.mein.drive.index;

import de.mein.drive.sql.DriveDatabaseManager;
import org.jdeferred.Deferred;

import java.util.concurrent.Executors;

/**
 * Created by xor on 11/24/16.
 */
public class StageIndexer extends BackgroundExecutor {

    private final DriveDatabaseManager databaseManager;

    public StageIndexer(DriveDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Deferred<Long, Exception, Void> indexStage(Long stageSetId) {
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor();
        }
        StageIndexerRunnable indexerRunnable = new StageIndexerRunnable(databaseManager, stageSetId);
        executorService.submit(indexerRunnable);
        return indexerRunnable.getPromise();
    }

}
