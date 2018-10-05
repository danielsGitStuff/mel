package de.mein.drive.service.sync;

import de.mein.Lok;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.file.AFile;
import de.mein.auth.service.ConnectResult;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.LockedRequest;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.tools.N;
import de.mein.auth.tools.Order;
import de.mein.drive.DriveSyncListener;
import de.mein.drive.data.*;
import de.mein.drive.data.conflict.ConflictSolver;
import de.mein.drive.jobs.CommitJob;
import de.mein.drive.jobs.SyncClientJob;
import de.mein.drive.quota.OutOfSpaceException;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.sql.*;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.drive.sql.dao.TransferDao;
import de.mein.drive.tasks.SyncTask;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.IOException;
import java.util.*;

/**
 * Created by xor on 10/27/16.
 */
@SuppressWarnings("Duplicates")
public class ClientSyncHandler extends SyncHandler {

    private final DriveClientSettingsDetails clientSttings;
    private DriveSyncListener syncListener;
    private MeinDriveClientService meinDriveService;
    private Map<String, ConflictSolver> conflictSolverMap = new keks<>();
    private Map<Long, Set<ConflictSolver>> relatedSolvers = new HashMap<>();

    public void updateHashes(Set<String> hashes) {
        FsDao fsDao = driveDatabaseManager.getFsDao();
        StageDao stageDao = driveDatabaseManager.getStageDao();
        TransferDao transferDao = driveDatabaseManager.getTransferDao();
        fsDao.lockWrite();
        N.forEach(hashes, s -> {
            // if is stage from server or is transfer -> flag as available
            N.forEach(stageDao.getUpdateStageSetsFromServer(), stageSet -> {
                stageDao.devUpdateSyncedByHashSet(stageSet.getId().v(), hashes);
            });
            DriveClientSettingsDetails clientSettings = driveSettings.getClientSettings();
            transferDao.updateAvailableByHashSet(clientSettings.getServerCertId(), clientSettings.getServerServiceUuid(), hashes);
        });
        fsDao.unlockWrite();
        transferManager.research();
    }

    private class keks<K, V> extends HashMap<K, V> {
        @Override
        public V put(K key, V value) {
            return super.put(key, value);
        }
    }

    public void setSyncListener(DriveSyncListener syncListener) {
        this.syncListener = syncListener;
    }


    public ClientSyncHandler(MeinAuthService meinAuthService, MeinDriveClientService meinDriveService) {
        super(meinAuthService, meinDriveService);
        this.meinDriveService = meinDriveService;
        this.clientSttings = meinDriveService.getDriveSettings().getClientSettings();
    }


    /**
     * call this if you are the receiver
     */
    @Deprecated
    private void setupTransfer() {
        try {
            Lok.warn("DEPRECATED");
//            N.sqlResource(fsDao.getNonSyncedFilesResource(), resource -> {
//                FsFile fsFile = resource.getNext();
//                while (fsFile != null) {
//                    //todo debug
//                    if (fsFile.getContentHash().v().equals("238810397cd86edae7957bca350098bc"))
//                        Lok.debug("TransferDao.insert.debugmic3n0fv");
//                    TransferDetails transfer = new TransferDetails();
//                    transfer.getServiceUuid().v(driveSettings.getClientSettings().getServerServiceUuid());
//                    transfer.getCertId().v(driveSettings.getClientSettings().getServerCertId());
//                    transfer.getHash().v(fsFile.getContentHash());
//                    transfer.getSize().v(fsFile.getSize());
//                    // this might fail due to unique constraint violation.
//                    // happens if you created two identical files at once.
//                    // no problem here
//                    try {
//                        transferManager.createTransfer(transfer);
//                    } catch (SqlQueriesException e) {
//                        System.err.println("ClientSyncHandler.setupTransfer.exception: " + e.getMessage());
//                    }
//                    fsFile = resource.getNext();
//                }
//            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        Lok.debug("ClientSyncHandler.setupTransfer.done: starting...");
        transferManager.research();
    }

//    /**
//     * Sends the StageSet to the server and updates it with the FsIds provided by the server.
//     * blocks until server has answered or the attempt failed.
//     *
//     * @param stageSetId
//     * @throws SqlQueriesException
//     * @throws InterruptedException
//     */
//    @SuppressWarnings("unchecked")
//    public void syncWithServer(Long stageSetId) throws SqlQueriesException, InterruptedException {
//        // stage is complete. first lock on FS
//        FsDao fsDao = driveDatabaseManager.getFsDao();
//        StageDao stageDao = driveDatabaseManager.getStageDao();
//        WaitLock waitLock = new WaitLock();
////        fsDao.unlockRead();
//        //fsDao.lockWrite();
//        stageDao.lockRead();
//
//        if (stageDao.stageSetHasContent(stageSetId)) {
//            waitLock.lock();
//            //all other stages we can find at this point are complete/valid and wait at this point.
//            //todo conflict checking goes here - has to block
//
//            Promise<MeinValidationProcess, Exception, Void> connectedPromise = meinAuthService.connect(driveSettings.getClientSettings().getServerCertId());
//            connectedPromise.done(mvp -> N.r(() -> {
//                // load to cached data structure
//                Commit commit = new Commit();
//                N.readSqlResource(driveDatabaseManager.getStageDao().getStagesByStageSetForCommitResource(stageSetId), (sqlResource, stage) -> commit.add(stage));
//                commit.setServiceUuid(meinDriveService.getUuid());
//                commit.setBasedOnVersion(driveDatabaseManager.getLatestVersion());
//                mvp.request(driveSettings.getClientSettings().getServerServiceUuid(), DriveStrings.INTENT_COMMIT, commit).done(result -> N.r(() -> {
//                    //fsDao.lockWrite();
//                    CommitAnswer answer = (CommitAnswer) result;
//                    for (Long stageId : answer.getStageIdFsIdMap().keySet()) {
//                        Long fsId = answer.getStageIdFsIdMap().get(stageId);
//                        Stage stage = stageDao.getStageById(stageId);
//                        stage.setFsId(fsId);
//                        if (stage.getParentId() != null && stage.getFsParentId() == null) {
//                            Long fsParentId = answer.getStageIdFsIdMap().get(stage.getParentId());
//                            stage.setFsParentId(fsParentId);
//                        }
//                        stageDao.update(stage);
//                    }
//                    StageSet stageSet = stageDao.getStageSetById(stageSetId);
//                    stageSet.setStatus(DriveStrings.STAGESET_STATUS_STAGED);
//                    stageSet.setSource(DriveStrings.STAGESET_SOURCE_SERVER);
////                    stageDao.updateStageSet(stageSet);
////                    meinDriveService.addJob(new CommitJob());
//                    commitStage(stageSetId, false);
//                    setupTransfer();
//                    transferManager.research();
//                    waitLock.unlock();
//                    //fsDao.unlockWrite();
//                }))
//                        .fail(result -> {
//                            if (result instanceof TooOldVersionException) {
//                                Lok.debug("ClientSyncHandler.syncWithServer");
//                                N.r(() -> {
//                                    // check if the new server version is already present
//                                    TooOldVersionException tooOldVersionException = (TooOldVersionException) result;
//                                    Lok.debug("ClientSyncHandler.syncWithServer.TooOldVersionException");
//                                    Long latestVersion = driveDatabaseManager.getLatestVersion();
//                                    if (latestVersion < tooOldVersionException.getNewVersion()) {
//                                        latestVersion = stageDao.getLatestStageSetVersion();
//                                        if (latestVersion == null || latestVersion < tooOldVersionException.getNewVersion()) {
//                                            meinDriveService.addJob(new SyncClientJob(tooOldVersionException.getNewVersion()));
//                                            //syncFromServer(tooOldVersionException.getNewVersion());
//                                        }
//                                    }
//                                    Lok.debug("ClientSyncHandler.syncWithServer");
////                                    meinDriveService.addJob(new SyncClientJob());
//                                });
//                            }
//                            waitLock.unlock();
//                        });
//            }));
//            connectedPromise.fail(ex -> {
//                // todo server did not commit. it probably had a local change. have to solve it here
//                System.err.println("MeinDriveClientService.startIndexer.could not connect :( due to: " + ex.getMessage());
//                // fsDao.unlockWrite();
//                stageDao.unlockRead();
//                waitLock.unlock();
//                meinDriveService.onSyncFailed();
//            });
//        } else {
//            stageDao.deleteStageSet(stageSetId);
//            stageDao.unlockRead();
//        }
//        waitLock.lock();
//    }

    /**
     * Sends the StageSet to the server and updates it with the FsIds provided by the server.
     * blocks until server has answered or the attempt failed.
     *
     * @param stageSetId
     * @throws SqlQueriesException
     * @throws InterruptedException
     */
    @SuppressWarnings("unchecked")
    public void syncToServerLocked(Long stageSetId) throws SqlQueriesException, InterruptedException {
        // stage is complete. first lock on FS
        FsDao fsDao = driveDatabaseManager.getFsDao();
        StageDao stageDao = driveDatabaseManager.getStageDao();
//        fsDao.unlockRead();
        //fsDao.lockWrite();
        stageDao.lockRead();
        try {
            if (stageDao.stageSetHasContent(stageSetId)) {
                //all other stages we can find at this point are complete/valid and wait at this point.
                //todo conflict checking goes here - has to block
                ConnectResult connectResult = meinAuthService.connectLocked(clientSttings.getServerCertId());
                if (connectResult.successful()) N.r(() -> {
                    // load to cached data structure
                    Commit commit = new Commit(meinDriveService.getCacheDirectory(), DriveSettings.CACHE_LIST_SIZE);
                    N.readSqlResource(driveDatabaseManager.getStageDao().getStagesByStageSetForCommitResource(stageSetId), (sqlResource, stage) -> commit.add(stage));
                    commit.setServiceUuid(meinDriveService.getUuid());
                    commit.setBasedOnVersion(driveDatabaseManager.getLatestVersion());
                    MeinValidationProcess mvp = connectResult.getValidationProcess();
                    LockedRequest<CommitAnswer> lockedRequest = mvp.requestLocked(clientSttings.getServerServiceUuid(), DriveStrings.INTENT_COMMIT, commit);
                    if (lockedRequest.successful()) {
//fsDao.lockWrite();
                        CommitAnswer answer = lockedRequest.getResponse();
                        for (Long stageId : answer.getStageIdFsIdMap().keySet()) {
                            Long fsId = answer.getStageIdFsIdMap().get(stageId);
                            Stage stage = stageDao.getStageById(stageId);
                            stage.setFsId(fsId);
                            if (stage.getParentId() != null && stage.getFsParentId() == null) {
                                Long fsParentId = answer.getStageIdFsIdMap().get(stage.getParentId());
                                stage.setFsParentId(fsParentId);
                            }
                            stageDao.update(stage);
                        }
                        StageSet stageSet = stageDao.getStageSetById(stageSetId);
                        stageSet.setStatus(DriveStrings.STAGESET_STATUS_STAGED);
                        stageSet.setSource(DriveStrings.STAGESET_SOURCE_SERVER);
//                    stageDao.updateStageSet(stageSet);
//                    meinDriveService.addJob(new CommitJob());
                        commitStage(stageSetId, false);
                        setupTransfer();
                        transferManager.research();
                        //fsDao.unlockWrite();
                    } else {
                        Exception result = lockedRequest.getException();
                        if (result instanceof TooOldVersionException) {
                            Lok.debug("ClientSyncHandler.syncWithServer");
                            N.r(() -> {
                                // check if the new server version is already present
                                TooOldVersionException tooOldVersionException = (TooOldVersionException) result;
                                Lok.debug("ClientSyncHandler.syncWithServer.TooOldVersionException");
                                Long latestVersion = driveDatabaseManager.getLatestVersion();
                                if (latestVersion < tooOldVersionException.getNewVersion()) {
                                    latestVersion = stageDao.getLatestStageSetVersion();
                                    if (latestVersion == null || latestVersion < tooOldVersionException.getNewVersion()) {
                                        meinDriveService.addJob(new SyncClientJob(tooOldVersionException.getNewVersion()));
                                        //syncFromServer(tooOldVersionException.getNewVersion());
                                    }
                                }
                                Lok.debug("ClientSyncHandler.syncWithServer");
//                                    meinDriveService.addJob(new SyncClientJob());
                            });
                        }
                    }
                    commit.cleanUp();
                });
                else {
                    // todo server did not commit. it probably had a local change. have to solve it here
                    Exception ex = connectResult.getException();
                    System.err.println("MeinDriveClientService.startIndexer.could not connect :( due to: " + ex.getMessage());
                    // fsDao.unlockWrite();
                    stageDao.unlockRead();
                    meinDriveService.onSyncFailed();
                }
            } else {
                stageDao.deleteStageSet(stageSetId);
            }
        } finally {
            stageDao.unlockRead();
        }
    }

    /**
     * Merges all staged StageSets (from file system) and checks the result for conflicts
     * with the stage from the server (if it exists).
     * If conflicts are resolved, server StageSet is committed.
     * if a single staged StageSet from file system remains it is send to the server
     * and a new CommitJob added to the MeinDriveServices working queue.
     *
     * @param commitJob
     */
    public void commitJob(CommitJob commitJob) {
        Lok.debug("ClientSyncHandler.commitJob");
        try {
            // first wait until every staging stuff is finished.
            fsDao.lockRead();

            List<StageSet> stagedFromFs = stageDao.getStagedStageSetsFromFS();
            Lok.debug("ClientSyncHandler.commitJob");

            // first: merge everything which has been analysed by the indexer
            mergeStageSets(stagedFromFs);

            stagedFromFs = stageDao.getStagedStageSetsFromFS();
            if (stagedFromFs.size() > 1) {
                meinDriveService.addJob(new CommitJob());
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            fsDao.unlockRead();
        }
        try {
            // ReadLock bis hier
            // update from server
            //fsDao.unlockRead();
            fsDao.lockWrite();
            // conflict check

            // if no conflict occurred, commit if we all staged StageSets have been merged
            List<StageSet> updateSets = stageDao.getUpdateStageSetsFromServer();
            List<StageSet> stagedFromFs = stageDao.getStagedStageSetsFromFS();
            // List<StageSet> committedStageSets = stageDao.getCommittedStageSets();
            if (updateSets.size() > 1) {
                System.err.println("ClientSyncHandler.commitJob.something went seriously wrong");
                stageDao.deleteServerStageSets();
                meinDriveService.addJob(new SyncClientJob());
                return;
            }
            if (updateSets.size() == 1 && stagedFromFs.size() == 1) {
                // method should create a new CommitJob with conflict solving details
                handleConflict(updateSets.get(0), stagedFromFs.get(0));
                setupTransfer();
                Lok.debug("setupTransfers() was here before");
                //transferManager.research();
                return;
            } else if (stagedFromFs.size() == 1) {
                //method should create a new CommitJob ? method blocks
                syncToServerLocked(stagedFromFs.get(0).getId().v());
                return;
            } else if (stagedFromFs.size() > 1) {
                // merge again
                meinDriveService.addJob(new CommitJob());
                return;
            } else if (updateSets.size() == 0 && commitJob.getSyncAnyway()) {
                syncFromServer(null);
                return;
            } else if (updateSets.size() == 1) {
                StageSet stageSet = updateSets.get(0);
                if (stageSet.getSource().equalsValue(DriveStrings.STAGESET_SOURCE_SERVER)) {
                    N.r(() -> commitStage(stageSet.getId().v()));
                    setupTransfer();
                    transferManager.research();
                }
            } else if (commitJob.getSyncAnyway() && stagedFromFs.size() == 0 && updateSets.size() == 0) {
                syncFromServer(null);
                return;
            }


            // lets assume that everything went fine!
            boolean hasCommitted = false;
            for (StageSet stageSet : updateSets) {
                if (stageDao.stageSetHasContent(stageSet.getId().v())) {
                    try {
                        commitStage(stageSet.getId().v(), false);
                        setupTransfer();
                        hasCommitted = true;
                    } catch (OutOfSpaceException e) {
                        e.printStackTrace();
                        meinDriveService.onInsufficientSpaceAvailable(stageSet.getId().v());
                    }
                } else
                    stageDao.deleteStageSet(stageSet.getId().v());
            }
            // new job in case we have solved conflicts in this run.
            // they must be committed to the server
            if (hasCommitted)
                meinDriveService.addJob(new CommitJob());
            // we are done here
            meinDriveService.onSyncDone();
        } catch (SqlQueriesException | InterruptedException e) {
            e.printStackTrace();
            meinDriveService.onSyncFailed();
        } finally {
            fsDao.unlockWrite();
        }

    }


    /**
     * check whether or not there are any conflicts between stuff that happened on this computer and stuff
     * that happened on the server. this will block until all conflicts are resolved.
     *
     * @param serverStageSet
     * @param stagedFromFs
     */
    private void handleConflict(StageSet serverStageSet, StageSet stagedFromFs) throws SqlQueriesException {
        String identifier = ConflictSolver.createIdentifier(serverStageSet.getId().v(), stagedFromFs.getId().v());
        ConflictSolver conflictSolver;
        // check if there is a solved ConflictSolver available. if so, use it. if not, make a new one.
        if (conflictSolverMap.containsKey(identifier)) {
            conflictSolver = conflictSolverMap.get(identifier);
            if (conflictSolver.isSolved()) {
                //todo debug
                if (serverStageSet.getId().v() == 3 && stagedFromFs.getId().v() == 6)
                    Lok.debug("ClientSyncHandler.handleConflict.debung0vbgiw435g");
                iterateStageSets(serverStageSet, stagedFromFs, null, conflictSolver);
                conflictSolver.setSolving(false);
            } else {
                System.err.println(getClass().getSimpleName() + ".handleConflict(): conflict " + identifier + " was not resolved");
//                conflictSolverMap.remove(identifier);
//                conflictSolver = new ConflictSolver(driveDatabaseManager, serverStageSet, stagedFromFs);
//                conflictSolver.beforeStart(serverStageSet);
            }
        } else {
            conflictSolver = new ConflictSolver(driveDatabaseManager, serverStageSet, stagedFromFs);
            conflictSolver.beforeStart(serverStageSet);
            iterateStageSets(serverStageSet, stagedFromFs, conflictSolver, null);
        }
        // only remember the conflict solver if it actually has conflicts
        if (!conflictSolver.isSolving()) {
            if (conflictSolver.hasConflicts()) {
                System.err.println("conflicts!!!!1!");
                putConflictSolver(conflictSolver);
                conflictSolver.setSolving(true);
                meinDriveService.onConflicts();
            } else {
                // todo FsDir hash conflicts
                conflictSolver.directoryStuff();
                //todo debug
                if (conflictSolver.getMergeStageSet().getId().v() == 8)
                    Lok.warn("debug");
                try {
                    this.commitStage(serverStageSet.getId().v());
                    this.deleteObsolete(conflictSolver);
                } catch (OutOfSpaceException e) {
                    e.printStackTrace();
                    meinDriveService.onInsufficientSpaceAvailable(serverStageSet.getId().v());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                conflictSolver.cleanup();
//                setupTransfer();
//                transferManager.research();
                Long mergedId = conflictSolver.getMergeStageSet().getId().v();
                this.minimizeStage(mergedId);
                if (stageDao.stageSetHasContent(mergedId))
                    meinDriveService.addJob(new CommitJob());
                else {
                    stageDao.deleteStageSet(mergedId);
                }
            }
        }
    }

    private void deleteObsolete(ConflictSolver conflictSolver) throws SqlQueriesException, IOException {
        N.readSqlResource(stageDao.getObsoleteFileStagesResource(conflictSolver.getObsoleteStageSet().getId().v()), (sqlResource, stage) -> {
            AFile file = stageDao.getFileByStage(stage);
            if (file != null && file.exists()) {
                wastebin.deleteUnknown(file);
            }
        });
        N.readSqlResource(stageDao.getObsoleteDirStagesResource(conflictSolver.getObsoleteStageSet().getId().v()), (sqlResource, stage) -> {
            AFile file = stageDao.getFileByStage(stage);
            if (file != null && file.exists()) {
                wastebin.deleteUnknown(file);
            }
        });
    }

    private void putConflictSolver(ConflictSolver conflictSolver) {
        // search for related ConflictSolvers before. they deprecate as we insert the new one.
        deleteRelated(conflictSolver.getServerStageSet().getId().v());
        deleteRelated(conflictSolver.getLocalStageSet().getId().v());
        addRelated(conflictSolver, conflictSolver.getLocalStageSet().getId().v());
        addRelated(conflictSolver, conflictSolver.getServerStageSet().getId().v());
        conflictSolverMap.put(conflictSolver.getIdentifier(), conflictSolver);
    }

    private void addRelated(ConflictSolver solver, Long stageSetId) {
        if (relatedSolvers.containsKey(stageSetId)) {
            relatedSolvers.get(stageSetId).add(solver);
        } else {
            Set<ConflictSolver> set = new HashSet<>();
            set.add(solver);
            relatedSolvers.put(stageSetId, set);
        }
    }

    private void deleteRelated(Long stageSetId) {
        if (relatedSolvers.containsKey(stageSetId)) {
            Set<ConflictSolver> solvers = relatedSolvers.remove(stageSetId);
            for (ConflictSolver solver : solvers) {
                conflictSolverMap.remove(solver.getIdentifier());
            }
        }
    }

    private void minimizeStage(Long stageSetId) throws SqlQueriesException {
        if (stageSetId == 9)
            Lok.warn("debug");
        N.sqlResource(stageDao.getStagesResource(stageSetId), stages -> {
            Stage stage = stages.getNext();
            while (stage != null) {
                Long fsId = stage.getFsId();
                if (fsId != null) {
                    FsEntry fsEntry = fsDao.getGenericById(fsId);
                    boolean deleteStage = false;
                    if (fsEntry != null) {
                        if (!stage.getDeleted() && fsEntry.getContentHash().v().equals(stage.getContentHash()))
                            deleteStage = true;
                    }
                    if (deleteStage)
                        stageDao.deleteStageById(stage.getId());
//                    if (stage.getDeleted() && (fsEntry != null && fsEntry.getContentHash().v().equals(stage.getContentHash()))) {
//                        stageDao.deleteStageById(stage.getId());
//                    }
                }
                stage = stages.getNext();
            }
        });
    }

    /**
     * merges two {@link StageSet}s to a new one
     *
     * @param stageSets
     * @throws SqlQueriesException
     */
    private void mergeStageSets(List<StageSet> stageSets) throws SqlQueriesException {
        if (stageSets.size() > 2)
            System.err.println("ClientSyncHandler.mergeStageSets.TOO MANY!!!1");
        if (stageSets.size() <= 1)
            return;
        StageSet lStageSet = stageSets.get(0);
        StageSet rStageSet = stageSets.get(1);
        Lok.debug("ClientSyncHandler.mergeStageSets L: " + lStageSet.getId().v() + " R: " + rStageSet.getId().v());
        StageSet mStageSet = stageDao.createStageSet(DriveStrings.STAGESET_SOURCE_MERGED, lStageSet.getOriginCertId().v(), lStageSet.getOriginServiceUuid().v()
                , lStageSet.getVersion().v());
        final Long mStageSetId = mStageSet.getId().v();
        SyncStageMerger merger = new SyncStageMerger(lStageSet.getId().v(), rStageSet.getId().v()) {
            private Order order = new Order();
            private Map<Long, Long> idMapRight = new HashMap<>();
            private Map<Long, Long> idMapLeft = new HashMap<>();

            @Override
            public void stuffFound(Stage left, Stage right, AFile lFile, AFile rFile) throws SqlQueriesException {
                if (left != null) {
                    //todo debug
                    if (left.getId() == 64)
                        Lok.debug("ClientSyncHandler.stuffFound.debugfin30f");
                    if (right != null) {
                        Stage stage = new Stage().setOrder(order.ord()).setStageSet(mStageSetId);
                        stage.mergeValuesFrom(right);
                        // do not forget to relate to the parent!
                        if (idMapRight.containsKey(right.getParentId()))
                            stage.setParentId(idMapRight.get(right.getParentId()));
                        stageDao.insert(stage);
                        idMapRight.put(right.getId(), stage.getId());
                        idMapLeft.put(left.getId(), stage.getId());
                        stageDao.flagMerged(right.getId(), true);
                    } else {
                        // only merge if file exists
                        AFile fLeft = stageDao.getFileByStage(left);
//                        if (fLeft.exists() || left.getSynced()) {
                        Stage stage = new Stage().setOrder(order.ord()).setStageSet(mStageSetId);
                        stage.mergeValuesFrom(left);
                        if (idMapLeft.containsKey(left.getParentId()))
                            stage.setParentId(idMapLeft.get(left.getParentId()));
                        stageDao.insert(stage);
                        idMapLeft.put(left.getId(), stage.getId());
//                        }
                    }
                } else {
                    if (right != null) {
                        if (right.getParentId() == null) {
                            // stage is completely unconnected to whatever is on the left side
                            Stage stage = new Stage().setOrder(order.ord()).setStageSet(mStageSetId);
                            stage.mergeValuesFrom(right);
                            stageDao.insert(stage);
                            stageDao.flagMerged(right.getId(), true);
                        } else {
                            /**
                             * We're iterating top down through the StageSet.
                             * We probably will find the parent on the left side and therefore have
                             * to "attach" our new left Stage to it.
                             */
                            Stage stage = new Stage().setOrder(order.ord()).setStageSet(mStageSetId);
                            stage.mergeValuesFrom(right);
                            if (right.getParentId() == null) {
                                stageDao.insert(stage);
                            } else {
                                Stage rParent = stageDao.getStageById(right.getParentId());
                                AFile rParentFile = stageDao.getFileByStage(rParent);
                                Stage lParent = stageDao.getStageByPath(mStageSetId, rParentFile);
                                if (lParent != null) {
                                    stage.setParentId(lParent.getId());
                                } else {
                                    System.err.println("ClientSyncHandler.stuffFound.8c8h38h9");
                                }
                                stageDao.insert(stage);
                            }
                            //todo debug
                            if (idMapRight.containsKey(right.getId()))
                                Lok.debug("ClientSyncHandler.stuffFound.debugj9f3jß");

                            idMapRight.put(right.getId(), stage.getId());
                        }
                    }
                }
            }
        };
        iterateStageSets(lStageSet, rStageSet, merger, null);
        stageDao.deleteStageSet(rStageSet.getId().v());
        stageDao.deleteStageSet(lStageSet.getId().v());
        stageDao.updateStageSet(mStageSet.setStatus(DriveStrings.STAGESET_STATUS_STAGED).setSource(DriveStrings.STAGESET_SOURCE_FS));
        // tell the service which StageSets had been merged
        meinDriveService.onStageSetsMerged(lStageSet.getId().v(), rStageSet.getId().v(), mStageSet);
    }

    /**
     * iterates over the left {@link StageSet} first and finds
     * relating entries in the right {@link StageSet} and flags everything it finds (in the right StageSet).
     * Afterwards it iterates over all non flagged {@link Stage}s of the right {@link StageSet}.
     * Iteration is in order of the Stages insertion.
     *
     * @param lStageSet
     * @param rStageSet
     * @param merger
     * @throws SqlQueriesException
     */
    @SuppressWarnings("Duplicates")
    private void iterateStageSets(StageSet lStageSet, StageSet rStageSet, SyncStageMerger merger, ConflictSolver conflictSolver) throws SqlQueriesException {
        N.sqlResource(stageDao.getStagesResource(lStageSet.getId().v()), lStages -> {
            Stage lStage = lStages.getNext();
            while (lStage != null) {
                //todo debug
                if (lStage.getName().equals("same2.txt"))
                    Lok.debug("ClientSyncHandler.iterateStageSets.debugmf03nm");
                AFile lFile = stageDao.getFileByStage(lStage);
                Stage rStage = stageDao.getStageByPath(rStageSet.getId().v(), lFile);
                if (conflictSolver != null)
                    conflictSolver.solve(lStage, rStage);
                else {
                    AFile rFile = (rStage != null) ? AFile.instance(lFile.getAbsolutePath()) : null;
                    merger.stuffFound(lStage, rStage, lFile, rFile);
                }
                if (rStage != null)
                    stageDao.flagMerged(rStage.getId(), true);
                lStage = lStages.getNext();
            }
        });
        N.sqlResource(stageDao.getNotMergedStagesResource(rStageSet.getId().v()), rStages -> {
            Stage rStage = rStages.getNext();
            while (rStage != null) {
                if (conflictSolver != null)
                    conflictSolver.solve(null, rStage);
                else {
                    AFile rFile = stageDao.getFileByStage(rStage);
                    merger.stuffFound(null, rStage, null, rFile);
                }
                rStage = rStages.getNext();
            }
        });
    }

    /**
     * pulls StageSet from the Server. locks.
     *
     * @param newVersion
     * @throws SqlQueriesException
     * @throws InterruptedException
     */
    public void syncFromServer(Long newVersion) throws SqlQueriesException, InterruptedException {
        runner.runTry(() -> {
            stageDao.deleteServerStageSets();
        });
        Certificate serverCert = meinAuthService.getCertificateManager().getTrustedCertificateById(clientSttings.getServerCertId());
        ConnectResult connectResult = meinAuthService.connectLocked(serverCert.getId().v());
        if (connectResult.successful()) {
            runner.runTry(() -> {
                MeinValidationProcess mvp = connectResult.getValidationProcess();
                long version = driveDatabaseManager.getDriveSettings().getLastSyncedVersion();
                StageSet stageSet = stageDao.createStageSet(DriveStrings.STAGESET_SOURCE_SERVER, clientSttings.getServerCertId(), clientSttings.getServerServiceUuid(), newVersion);
                //prepare cached answer
                SyncTask sentSyncTask = new SyncTask(meinDriveService.getCacheDirectory(), DriveSettings.CACHE_LIST_SIZE)
                        .setOldVersion(version);
                sentSyncTask.setServiceUuid(this.clientSttings.getServerServiceUuid());
                LockedRequest<SyncTask> requestResult = mvp.requestLocked(clientSttings.getServerServiceUuid(), DriveStrings.INTENT_SYNC, sentSyncTask);
                if (requestResult.successful()) runner.runTry(() -> {
                    SyncTask syncTask = requestResult.getResponse();
                    syncTask.setStageSet(stageSet);
                    syncTask.setSourceCertId(clientSttings.getServerCertId());
                    syncTask.setSourceServiceUuid(clientSttings.getServerServiceUuid());
                    //server might have gotten a new version in the mean time and sent us that
                    stageSet.setVersion(syncTask.getNewVersion());
                    stageDao.updateStageSet(stageSet);
                    Promise<Long, Void, Void> promise = this.sync2Stage(syncTask);
                    promise.done(nil -> runner.runTry(() -> {
                        meinDriveService.addJob(new CommitJob());
                        if (syncListener != null)
                            syncListener.onSyncDone();
                    })).fail(result -> {
                                System.err.println("ClientSyncHandler.syncFromServer.j99f49459f54");
                                N.r(() -> stageDao.deleteStageSet(stageSet.getId().v()));
                            }
                    );
                });
                else {
                    Lok.debug("ClientSyncHandler.syncFromServer.EXCEPTION: " + requestResult.getException());
                }
            });
        } else {
            Lok.debug("ClientSyncHandler.syncFromServer.debughv08e5hg");

        }
        Lok.debug("ClientSyncHandler.syncFromServer");
    }

    private void insertWithParentId(Map<Long, Long> entryIdStageIdMap, GenericFSEntry genericFSEntry, Stage stage) throws SqlQueriesException {
        if (entryIdStageIdMap.containsKey(genericFSEntry.getParentId().v())) {
            stage.setParentId(entryIdStageIdMap.get(genericFSEntry.getParentId().v()));
        }
        stageDao.insert(stage);
        entryIdStageIdMap.put(genericFSEntry.getId().v(), stage.getId());
    }

    /**
     * delta goes in here
     *
     * @param syncTask contains delta
     * @return stageSetId in Promise
     * @throws SqlQueriesException
     * @throws InterruptedException
     */
    private Promise<Long, Void, Void> sync2Stage(SyncTask syncTask) throws SqlQueriesException, InterruptedException {
        DeferredObject<Void, Void, Void> communicationDone = new DeferredObject<>();
        DeferredObject<Long, Void, Void> finished = new DeferredObject<>();
        Map<Long, Long> entryIdStageIdMap = new HashMap<>();
        Order order = new Order();
        syncTask.setCacheDirectory(meinDriveService.getCacheDirectory());
        Iterator<GenericFSEntry> iterator = syncTask.iterator();
        if (!iterator.hasNext()) {
            syncTask.cleanUp();
            finished.reject(null);
            return finished;
        }
        StageSet stageSet = syncTask.getStageSet();
        syncTask.setStageSetId(stageSet.getId().v());
        // stage first
        while (iterator.hasNext()) {
            GenericFSEntry genericFSEntry = iterator.next();
            Stage stage = GenericFSEntry.generic2Stage(genericFSEntry, stageSet.getId().v());
            //todo debug
            if (stage.getContentHashPair().equalsValue("51037a4a37730f52c8732586d3aaa316") && stageSet.getId().equalsValue(2L))
                Lok.warn("debug1");
            stage.setOrder(order.ord());

//            //todo duplicate
//            if (stage.getFsIdPair().notNull() && !stage.getIsDirectory() && fsDao.hasId(stage.getFsId())) {
//                FsEntry fsEntry = fsDao.getFile(stage.getFsId());
//                if (fsEntry.getContentHash().equalsValue())
//                stage.setSynced(fsEntry.getSynced().v());
//            } else if (genericFSEntry.getSynced().v()) {
//                stage.setSynced(true);
//            } else {
//                stage.setSynced(false);
//            }
            insertWithParentId(entryIdStageIdMap, genericFSEntry, stage);
        }
        syncTask.cleanUp();
        // check if something was deleted
        List<Stage> stages = stageDao.getDirectoriesByStageSet(stageSet.getId().v());
        Map<Long, FsDirectory> fsDirIdsToRetrieve = new HashMap<>();
        for (Stage stage : stages) {
            FsDirectory fsDirectory = fsDao.getDirectoryById(stage.getFsId());
            // if dir is not new check if something has been deleted
            if (fsDirectory != null) {
                String oldeHash = fsDirectory.getContentHash().v();
                List<Stage> subStages = stageDao.getSubStagesByFsDirectoryId(fsDirectory.getId().v(), stageSet.getId().v());
                List<GenericFSEntry> subFsEntries = fsDao.getContentByFsDirectory(fsDirectory.getId().v());
                Map<String, GenericFSEntry> fsNameMap = new HashMap<>();
                for (GenericFSEntry gen : subFsEntries) {
                    fsNameMap.put(gen.getName().v(), gen);
                }
                Map<String, Stage> stageNameMap = new HashMap<>();
                for (Stage s : subStages) {
                    stageNameMap.put(s.getName(), s);
                }
                for (GenericFSEntry genSub : subFsEntries) {
                    Stage subStage = stageNameMap.get(genSub.getName().v());
                    if (subStage != null) {
                        stageNameMap.remove(genSub.getName().v());
                        if (!subStage.getDeleted()) {
                            if (subStage.getIsDirectory() && genSub.getIsDirectory().v()) {
                                fsDirectory.addSubDirectory((FsDirectory) genSub.ins());
                            } else if (!subStage.getIsDirectory() && !genSub.getIsDirectory().v()) {
                                fsDirectory.addFile((FsFile) genSub.ins());
                            }
                        }
                    } else {
                        if (genSub.getIsDirectory().v()) {
                            fsDirectory.addSubDirectory((FsDirectory) genSub.ins());
                        } else {

                            fsDirectory.addFile((FsFile) genSub.ins());
                        }
                    }
                }
                for (String key : stageNameMap.keySet()) {
                    Stage newStage = stageNameMap.get(key);
                    if (newStage.getIsDirectory() && !newStage.getDeleted()) {
                        FsDirectory newDir = new FsDirectory();
                        newDir.getName().v(newStage.getName());
                        fsDirectory.addSubDirectory(newDir);
                    } else if (!newStage.getDeleted()) {
                        FsFile newFile = new FsFile();
                        newFile.getName().v(newStage.getName());
                        fsDirectory.addFile(newFile);
                    }
                }
                fsDirectory.calcContentHash();
                String hash = fsDirectory.getContentHash().v();
                if (!hash.equals(stage.getContentHash())) {
                    Lok.debug("ClientSyncHandler.syncFromServer.SOMETHING.DELETED?");
                    fsDirIdsToRetrieve.put(fsDirectory.getId().v(), fsDirectory);
                }
            }
        }
        if (fsDirIdsToRetrieve.size() > 0) {
            Promise<List<FsDirectory>, Exception, Void> promise = meinDriveService.requestDirectoriesByIds(fsDirIdsToRetrieve.keySet(), clientSttings.getServerCertId(), clientSttings.getServerServiceUuid());
            promise.done(directories -> runner.runTry(() -> {
                // stage everything not in the directories as deleted
                for (FsDirectory directory : directories) {
                    Set<Long> stillExistingIds = new HashSet<>();
                    for (FsFile fsFile : directory.getFiles())
                        stillExistingIds.add(fsFile.getId().v());
                    for (FsDirectory subDir : directory.getSubDirectories())
                        stillExistingIds.add(subDir.getId().v());
                    List<GenericFSEntry> fsDirs = fsDao.getContentByFsDirectory(directory.getId().v());
                    for (GenericFSEntry genSub : fsDirs) {
                        if (!stillExistingIds.contains(genSub.getId().v())) {
                            // genSub shall be deleted
                            Stage stage = GenericFSEntry.generic2Stage(genSub, stageSet.getId().v());
                            stage.setDeleted(true);
                            stage.setOrder(order.ord());
//                            //todo dubplicate 2
//                            if (stage.getFsId() != null && !stage.getIsDirectory() && fsDao.hasId(stage.getFsId())) {
//                                FsEntry fsEntry = fsDao.getFile(stage.getFsId());
//                                stage.setSynced(fsEntry.getSynced().v());
//                            } else {
//                                stage.setSynced(false);
//                            }
                            stage.setSynced(false);
                            insertWithParentId(entryIdStageIdMap, genSub, stage);
                            recursiveDeleteOnStage(entryIdStageIdMap, order, genSub, stage);
                        }
                    }
                }
//                setupTransfer();
                //stageDao.commitStage(stageSet.getId().v());
                communicationDone.resolve(null);
            }));
        } else {
//            setupTransfer();
            //stageDao.commitStage(stageSet.getId().v());
            communicationDone.resolve(null);
        }
        communicationDone.done(nul -> {
            stageSet.setStatus(DriveStrings.STAGESET_STATUS_STAGED);
            N.r(() -> stageDao.updateStageSet(stageSet));
            finished.resolve(stageSet.getId().v());
        }).fail(nul -> {
            finished.reject(null);
        });
        return finished;
    }

    private void recursiveDeleteOnStage(Map<Long, Long> entryIdStageIdMap, Order order, GenericFSEntry generic, Stage stage) throws SqlQueriesException {
        List<GenericFSEntry> content = fsDao.getContentByFsDirectory(generic.getId().v());
        for (GenericFSEntry subGen : content) {
            Stage subStage = GenericFSEntry.generic2Stage(subGen, stage.getStageSet());
            subStage.setDeleted(true);
            subStage.setOrder(order.ord());
            subStage.setParentId(stage.getId());
            if (!subStage.getIsDirectory())
                subStage.setSynced(false);
            stageDao.insert(subStage);
            entryIdStageIdMap.put(subGen.getId().v(), subStage.getId());
            if (subGen.getIsDirectory().v()) {
                recursiveDeleteOnStage(entryIdStageIdMap, order, subGen, subStage);
            }
        }
    }

    public void onStageSetsMerged(Long lStageSetId, Long rStageSetId, StageSet mergedStageSet) {
        for (ConflictSolver solver : conflictSolverMap.values()) {
            solver.checkObsolete(lStageSetId, rStageSetId);
        }
    }

    public Map<String, ConflictSolver> getConflictSolverMap() {
        return conflictSolverMap;
    }
}
