package de.mel.filesync.index.watchdog;

import de.mel.Lok;
import de.mel.auth.tools.Eva;
import de.mel.auth.tools.lock2.BunchOfLocks;
import de.mel.auth.tools.lock2.P;
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
    private final FileWatcher fileWatcher;
    private StageIndexer.StagingDoneListener stagingDoneListener;

    public StageIndexerRunnable(FileSyncDatabaseManager databaseManager, PathCollection pathCollection, FileWatcher fileWatcher) {
        super(databaseManager);
        this.pathCollection = pathCollection;
        this.fileWatcher = fileWatcher;
    }


    public FsDao getFsDao() {
        return fsDao;
    }


    @Override
    public void runImpl() {
        if (pathCollection.getPaths().size() > 0) {
//            Warden warden = P.confine(P.read(fsDao));
            BunchOfLocks bunchOfLocks = P.confine(fsDao);
            try {
                initStage(FileSyncStrings.STAGESET_SOURCE_FS, pathCollection.getPaths().iterator(), fileWatcher, databaseManager.getFileSyncSettings().getLastSyncedVersion());
                examineStage();
                Eva.runIf(() -> !StageIndexerRunnable.this.databaseManager.melFileSyncService.getFileSyncSettings().isServer(), () -> {
                    Lok.debug("SIR on client");
                });
                if (stageSetId != null)
                    stagingDoneListener.onStagingFsEventsDone(stageSetId);
            } catch (Exception e) {
                e.printStackTrace();
                // todo check for inotify exceeded. if so, stop the service
            } finally {
                bunchOfLocks.end();
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
