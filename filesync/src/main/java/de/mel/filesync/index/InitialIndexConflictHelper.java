package de.mel.filesync.index;

import de.mel.Lok;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.tools.Order;
import de.mel.auth.tools.lock2.BunchOfLocks;
import de.mel.filesync.data.FileSyncClientSettingsDetails;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.service.MelFileSyncClientService;
import de.mel.filesync.sql.FsEntry;
import de.mel.filesync.sql.GenericFSEntry;
import de.mel.filesync.sql.Stage;
import de.mel.filesync.sql.StageSet;
import de.mel.filesync.sql.dao.FsDao;
import de.mel.filesync.sql.dao.StageDao;
import de.mel.sql.RWLock;
import de.mel.sql.SqlQueriesException;

import java.util.HashMap;
import java.util.Map;

/**
 * When this is useful: when indexing the root directory while booting there still might be outstanding file transfers.
 * So there are files in the FS table but not yet on the file system (marked as not synced in the fs table). At least they shouldn't.
 * In case something put had placed conflicting files, this class creates a "fake" server {@link StageSet} conflicting with the {@link StageSet} from indexing on boot.
 */
public class InitialIndexConflictHelper {
    private final String uuid;
    private Long serverStageSetId;
    private StageDao stageDao;
    private FsDao fsDao;
    private StageSet serverStageSet;
    private final MelFileSyncClientService driveClientService;
    private Order ord;
    private StageSet fsStageSet;

    private static Map<String, InitialIndexConflictHelper> instanceMap = new HashMap<>();
    private IndexerRunnable indexerRunnable;

    public InitialIndexConflictHelper(MelFileSyncClientService driveClientService) {
        this.driveClientService = driveClientService;
        this.uuid = CertificateManager.randomUUID().toString();
        instanceMap.put(uuid, this);
    }

    public static void finished(String conflictHelperUuid) {
        if (instanceMap.containsKey(conflictHelperUuid)) {
            InitialIndexConflictHelper ch = instanceMap.remove(conflictHelperUuid);
            ch.indexerRunnable.getStartedDeferred().resolve(ch.indexerRunnable);
        }
    }

    /**
     * Creates a fake {@link StageSet}.
     * @param fsStageSet
     * @throws SqlQueriesException
     */
    public void onStart(StageSet fsStageSet) throws SqlQueriesException {
        //setup a fake stageset from server. we put files here that have not yet been synced but there were found unknown files in their places.
        this.fsStageSet = fsStageSet;
        stageDao = driveClientService.getFileSyncDatabaseManager().getStageDao();
        fsDao = driveClientService.getFileSyncDatabaseManager().getFsDao();
        FileSyncClientSettingsDetails clientSettings = driveClientService.getFileSyncSettings().getClientSettings();
        serverStageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_SERVER, FileSyncStrings.STAGESET_STATUS_STAGING, clientSettings.getServerCertId(), clientSettings.getServerServiceUuid(), null,driveClientService.getFileSyncSettings().getLastSyncedVersion() + 1);
        serverStageSetId = serverStageSet.getId().v();
        ord = new Order();
    }

    /**
     * Looks for {@link Stage}s conflicting with not synced fs entries and puts them into the fake {@link StageSet}.
     * @param fsEntry
     * @param stage
     * @throws SqlQueriesException
     */
    public void check(FsEntry fsEntry, Stage stage) throws SqlQueriesException {
        if (fsEntry != null && !fsEntry.getSynced().v() && !stage.getDeleted() && !fsEntry.getContentHash().equalsValue(stage.getContentHash())) {
            Stage stageParent = stage.getFsParentIdPair().notNull() ? stageDao.getStageParentByFsId(stage.getStageSet(), stage.getFsParentId()) : null;
            Stage parentCopy = null;
            if (stageParent != null) {
                parentCopy = new Stage(serverStageSetId, stageParent);
                stageParent.setOrder(ord.ord());
                stageDao.insert(stageParent);
            }
            GenericFSEntry genericFSEntry = fsDao.getGenericById(fsEntry.getId().v());
            Stage conflictStage = GenericFSEntry.generic2Stage(genericFSEntry, serverStageSetId);
            conflictStage.setOrder(ord.ord());
            if (parentCopy != null)
                conflictStage.setParentId(parentCopy.getId());
            stageDao.insert(conflictStage);
            Lok.debug();
        } else
            ord.ord();
    }

    /**
     * @param bunchOfLocks
     * @param indexerRunnable
     * @return false if no conflict occured and this can be ignored
     * @throws SqlQueriesException
     */
    public boolean onDone(BunchOfLocks bunchOfLocks, IndexerRunnable indexerRunnable) throws SqlQueriesException {
        this.indexerRunnable = indexerRunnable;
        serverStageSet.setStatus(FileSyncStrings.STAGESET_STATUS_STAGED);
        stageDao.updateStageSet(serverStageSet);
        //nothing happened, just return
        if (!stageDao.stageSetHasContent(serverStageSetId)) {
            stageDao.deleteStageSet(serverStageSetId);
            instanceMap.remove(uuid);
            return false;
        }
        RWLock lock = new RWLock();
        // give it the uuid so it can unlock the wait lock
        driveClientService.getSyncHandler().handleConflict(serverStageSet, fsStageSet, bunchOfLocks, uuid);
//        if (conflictSolvers.iterator().hasNext())

//        ConflictSolver conflictSolver = new ConflictSolver(driveClientService.getDriveDatabaseManager(), serverStageSet, fsStageSet);
//        conflictSolver.beforeStart(serverStageSet);
//        driveClientService.getSyncHandler().iterateStageSets(serverStageSet, fsStageSet, conflictSolver, null);
//        Lok.debug();
        Lok.debug();
        return true;
    }
}
