package de.mein.drive.index.watchdog;

import de.mein.Lok;
import de.mein.auth.tools.lock.T;
import de.mein.auth.tools.lock.Read;
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
            Transaction transaction = T.transaction(T.read(fsDao));
            try {
                //todo debug
                if (Thread.currentThread().getName().startsWith("StageIndexerRunnable for MeinDriveClientService for MA2"))
                    Lok.debug("StageIndexerRunnable.runImpl.debug23r2300");
                Lok.debug("StageIndexerRunnable.runImpl.locking read on " + Thread.currentThread().getName());
                Lok.debug("StageIndexerRunnable.runImpl.locked");
                initStage(DriveStrings.STAGESET_SOURCE_FS, pathCollection.getPaths().iterator(), indexWatchdogListener);
                //todo debug
                if (stageSetId == 4)
                    Lok.debug("AbstractIndexer.initStage.debugjfg3jhgw0");
                examineStage();
                transaction.end();
                unlocked = true;
                if (Thread.currentThread().getName().startsWith("StageIndexerRunnable[" + stageSetId + "] for MeinDriveClientService for MA2"))
                    Lok.debug("StageIndexerRunnable[" + stageSetId + "].runImpl.debug8fh384");
                if (stageSetId != null)
                    stagingDoneListener.onStagingFsEventsDone(stageSetId);
            } catch (Exception e) {
                e.printStackTrace();
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
