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
        boolean unlocked = false;
        try {
            //todo debug
            if(Thread.currentThread().getName().startsWith("StageIndexerRunnable for MeinDriveClientService for MA2"))
                System.out.println("StageIndexerRunnable.runImpl.debug23r2300");
            System.out.println("StageIndexerRunnable.runImpl.locking read on " + Thread.currentThread().getName());
            fsDao.lockRead();
            System.out.println("StageIndexerRunnable.runImpl.locked");
            initStage(DriveStrings.STAGESET_TYPE_FS, pathCollection.getPaths().stream());
            examineStage();
            fsDao.unlockRead();
            unlocked = true;
            if(Thread.currentThread().getName().startsWith("StageIndexerRunnable["+stageSetId+"] for MeinDriveClientService for MA2"))
                System.out.println("StageIndexerRunnable["+stageSetId+"].runImpl.debug8fh384");
            stagingDoneListener.onStagingFsEventsDone(stageSetId);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (!unlocked) {
                System.out.println("StageIndexerRunnable["+stageSetId+"].runImpl.unlocking on " + Thread.currentThread().getName());
                fsDao.unlockRead();
                System.out.println("StageIndexerRunnable["+stageSetId+"].runImpl.unlocked");
            }
        }
    }


    public void setStagingDoneListener(StageIndexer.StagingDoneListener stagingDoneListener) {
        assert stagingDoneListener != null;
        this.stagingDoneListener = stagingDoneListener;
    }
}
