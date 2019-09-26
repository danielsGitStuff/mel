package de.mel.drive.index;

import de.mel.Lok;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.tools.Order;
import de.mel.auth.tools.lock.Warden;
import de.mel.drive.data.DriveClientSettingsDetails;
import de.mel.drive.data.DriveStrings;
import de.mel.drive.service.MelDriveClientService;
import de.mel.drive.sql.FsEntry;
import de.mel.drive.sql.GenericFSEntry;
import de.mel.drive.sql.Stage;
import de.mel.drive.sql.StageSet;
import de.mel.drive.sql.dao.FsDao;
import de.mel.drive.sql.dao.StageDao;
import de.mel.sql.RWLock;
import de.mel.sql.SqlQueriesException;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class InitialIndexConflictHelper {
    private final String uuid;
    private Long serverStageSetId;
    private StageDao stageDao;
    private FsDao fsDao;
    private StageSet serverStageSet;
    private final MelDriveClientService driveClientService;
    private Order ord;
    private StageSet fsStageSet;

    private static Map<String, InitialIndexConflictHelper> instanceMap = new HashMap<>();
    private IndexerRunnable indexerRunnable;

    public InitialIndexConflictHelper(MelDriveClientService driveClientService) {
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

    public void onStart(StageSet fsStageSet) throws SqlQueriesException {
        //setup a fake stageset from server. we put files here that have not yet been synced but there were found unknown files in their places.
        this.fsStageSet = fsStageSet;
        stageDao = driveClientService.getDriveDatabaseManager().getStageDao();
        fsDao = driveClientService.getDriveDatabaseManager().getFsDao();
        DriveClientSettingsDetails clientSettings = driveClientService.getDriveSettings().getClientSettings();
        serverStageSet = stageDao.createStageSet(DriveStrings.STAGESET_SOURCE_SERVER, DriveStrings.STAGESET_STATUS_STAGING, clientSettings.getServerCertId(), clientSettings.getServerServiceUuid(), null,driveClientService.getDriveSettings().getLastSyncedVersion() + 1);
        serverStageSetId = serverStageSet.getId().v();
        ord = new Order();
    }

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
     * @param warden
     * @param indexerRunnable
     * @return false if no conflict occured and this can be ignored
     * @throws SqlQueriesException
     */
    public boolean onDone(Warden warden, IndexerRunnable indexerRunnable) throws SqlQueriesException {
        this.indexerRunnable = indexerRunnable;
        serverStageSet.setStatus(DriveStrings.STAGESET_STATUS_STAGED);
        stageDao.updateStageSet(serverStageSet);
        //nothing happened, just return
        if (!stageDao.stageSetHasContent(serverStageSetId)) {
            stageDao.deleteStageSet(serverStageSetId);
            instanceMap.remove(uuid);
            return false;
        }
        RWLock lock = new RWLock();
        // give it the uuid so it can unlock the wait lock
        driveClientService.getSyncHandler().handleConflict(serverStageSet, fsStageSet, warden, uuid);
//        if (conflictSolvers.iterator().hasNext())

//        ConflictSolver conflictSolver = new ConflictSolver(driveClientService.getDriveDatabaseManager(), serverStageSet, fsStageSet);
//        conflictSolver.beforeStart(serverStageSet);
//        driveClientService.getSyncHandler().iterateStageSets(serverStageSet, fsStageSet, conflictSolver, null);
//        Lok.debug();
        Lok.debug();
        return true;
    }
}
