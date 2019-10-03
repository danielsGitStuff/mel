package de.mel.filesync.index.watchdog;

import de.mel.Lok;
import de.mel.auth.tools.lock.P;
import de.mel.auth.tools.lock.Warden;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.data.PathCollection;
import de.mel.filesync.index.AbstractIndexer;
import de.mel.filesync.sql.FileSyncDatabaseManager;
import de.mel.filesync.sql.dao.FsDao;

/**
 * Locks fsDao for reading
 * Created by xor on 11/24/16.
 */
public class StageIndexerRunnable extends AbstractIndexer {

    private final PathCollection pathCollection;
    private final IndexWatchdogListener indexWatchdogListener;
    private StageIndexer.StagingDoneListener stagingDoneListener;

    public StageIndexerRunnable(FileSyncDatabaseManager databaseManager, PathCollection pathCollection, IndexWatchdogListener indexWatchdogListener) {
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
            Warden warden = P.confine(P.read(fsDao));
            try {
                initStage(FileSyncStrings.STAGESET_SOURCE_FS, pathCollection.getPaths().iterator(), indexWatchdogListener, databaseManager.getFileSyncSettings().getLastSyncedVersion());
                examineStage();
                warden.end();
                unlocked = true;
                if (stageSetId != null)
                    stagingDoneListener.onStagingFsEventsDone(stageSetId);
            } catch (Exception e) {
                e.printStackTrace();
                // todo check for inotify exceeded. if so, stop the service
            } finally {
                if (!unlocked) {
                    Lok.debug("StageIndexerRunnable[" + stageSetId + "].runImpl.unlocking on " + Thread.currentThread().getName());
                    warden.end();
                    Lok.debug("StageIndexerRunnable[" + stageSetId + "].runImpl.unlocked");
                }
                warden.end();
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
