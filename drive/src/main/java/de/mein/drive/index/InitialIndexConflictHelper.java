package de.mein.drive.index;

import de.mein.Lok;
import de.mein.auth.tools.Order;
import de.mein.auth.tools.lock.T;
import de.mein.auth.tools.lock.Transaction;
import de.mein.drive.data.DriveClientSettingsDetails;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.data.conflict.Conflict;
import de.mein.drive.data.conflict.ConflictSolver;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.sql.FsEntry;
import de.mein.drive.sql.GenericFSEntry;
import de.mein.drive.sql.Stage;
import de.mein.drive.sql.StageSet;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.sql.SqlQueriesException;

public class InitialIndexConflictHelper {
    private Long serverStageSetId;
    private StageDao stageDao;
    private FsDao fsDao;
    private StageSet serverStageSet;
    private final MeinDriveClientService driveClientService;
    private Order ord;
    private StageSet fsStageSet;

    public InitialIndexConflictHelper(MeinDriveClientService driveClientService) {
        this.driveClientService = driveClientService;
    }

    public void onStart(StageSet fsStageSet) throws SqlQueriesException {
        //setup a fake stageset from server. we put files here that have not yet been synced but there were found unknown files in their places.
        this.fsStageSet = fsStageSet;
        stageDao = driveClientService.getDriveDatabaseManager().getStageDao();
        fsDao = driveClientService.getDriveDatabaseManager().getFsDao();
        DriveClientSettingsDetails clientSettings = driveClientService.getDriveSettings().getClientSettings();
        serverStageSet = stageDao.createStageSet(DriveStrings.STAGESET_SOURCE_SERVER, DriveStrings.STAGESET_STATUS_STAGING, clientSettings.getServerCertId(), clientSettings.getServerServiceUuid(), driveClientService.getDriveSettings().getLastSyncedVersion() + 1);
        serverStageSetId = serverStageSet.getId().v();
        ord = new Order();
    }

    public void check(FsEntry fsEntry, Stage stage) throws SqlQueriesException {
        if (fsEntry != null && !fsEntry.getSynced().v() && !stage.getDeleted() && !fsEntry.getContentHash().equalsValue(stage.getContentHash())) {
            GenericFSEntry genericFSEntry = fsDao.getGenericById(fsEntry.getId().v());
            Stage conflictStage = GenericFSEntry.generic2Stage(genericFSEntry, serverStageSetId);
            conflictStage.setOrder(ord.ord());
            stageDao.insert(conflictStage);
            Lok.debug();
        }
    }

    public void onDone(Transaction transaction) throws SqlQueriesException {
        serverStageSet.setStatus(DriveStrings.STAGESET_STATUS_STAGED);
        stageDao.updateStageSet(serverStageSet);
        driveClientService.getSyncHandler().handleConflict(serverStageSet, fsStageSet, transaction);
//        ConflictSolver conflictSolver = new ConflictSolver(driveClientService.getDriveDatabaseManager(), serverStageSet, fsStageSet);
//        conflictSolver.beforeStart(serverStageSet);
//        driveClientService.getSyncHandler().iterateStageSets(serverStageSet, fsStageSet, conflictSolver, null);
//        Lok.debug();
    }
}
