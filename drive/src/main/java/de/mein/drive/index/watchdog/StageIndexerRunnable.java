package de.mein.drive.index.watchdog;

import de.mein.Lok;
import de.mein.auth.tools.lock.T;
import de.mein.auth.tools.lock.Transaction;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.data.PathCollection;
import de.mein.drive.index.AbstractIndexer;
import de.mein.drive.sql.DriveDatabaseManager;
import de.mein.drive.sql.dao.FsDao;

/**
 * Locks fsDao for reading
 * Created by xor on 11/24/16.
 */
public class StageIndexerRunnable extends AbstractIndexer {

    private final PathCollection pathCollection;
    private final IndexWatchdogListener indexWatchdogListener;
    private StageIndexer.StagingDoneListener stagingDoneListener;

    public StageIndexerRunnable(DriveDatabaseManager databaseManager, PathCollection pathCollection, IndexWatchdogListener indexWatchdogListener) {
        super(databaseManager);
        this.pathCollection = pathCollection;
        this.indexWatchdogListener = indexWatchdogListener;
    }


    public FsDao getFsDao() {
        return fsDao;
    }


    @Override
    public void runImpl() {
        boolean unlocked = false;
        if (pathCollection.getPaths().size() > 0) {
            Transaction transaction = T.lockingTransaction(T.read(fsDao));
            try {
                initStage(DriveStrings.STAGESET_SOURCE_FS, pathCollection.getPaths().iterator(), indexWatchdogListener);
                examineStage();
                transaction.end();
                unlocked = true;
                if (stageSetId != null)
                    stagingDoneListener.onStagingFsEventsDone(stageSetId);
            } catch (Exception e) {
                e.printStackTrace();
                // todo check for inotify exceeded. if so, stop the service
            } finally {
                if (!unlocked) {
                    Lok.debug("StageIndexerRunnable[" + stageSetId + "].runImpl.unlocking on " + Thread.currentThread().getName());
                    transaction.end();
                    Lok.debug("StageIndexerRunnable[" + stageSetId + "].runImpl.unlocked");
                }
            }
        } else {
            Lok.debug("StageIndexerRunnable.runImpl.got.empty.pathcollection");
        }
    }


    public void setStagingDoneListener(StageIndexer.StagingDoneListener stagingDoneListener) {
        assert stagingDoneListener != null;
        this.stagingDoneListener = stagingDoneListener;
    }
}
