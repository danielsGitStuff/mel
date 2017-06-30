package de.mein.drive.index.watchdog;

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
    private StageIndexer.StagingDoneListener stagingDoneListener;

    public StageIndexerRunnable(DriveDatabaseManager databaseManager, PathCollection pathCollection) {
        super(databaseManager);

        this.pathCollection = pathCollection;
    }


    public FsDao getFsDao() {
        return fsDao;
    }


    @Override
    public void runImpl() {
        try {
            //todo debug
            System.out.println("StageIndexerRunnable.runImpl.locking read on " + Thread.currentThread().getName());
            fsDao.lockRead();
            System.out.println("StageIndexerRunnable.runImpl.locked");
            initStage(DriveStrings.STAGESET_TYPE_FS, pathCollection.getPaths().stream());
            examineStage();
            stagingDoneListener.onStagingFsEventsDone(stageSetId);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("StageIndexerRunnable.runImpl.unlocking on " + Thread.currentThread().getName());
            fsDao.unlockRead();
            System.out.println("StageIndexerRunnable.runImpl.unlocked");

        }
    }


    public void setStagingDoneListener(StageIndexer.StagingDoneListener stagingDoneListener) {
        assert stagingDoneListener != null;
        this.stagingDoneListener = stagingDoneListener;
    }
}
