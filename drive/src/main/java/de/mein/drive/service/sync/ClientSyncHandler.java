package de.mein.drive.service.sync;

import de.mein.auth.data.db.Certificate;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.auth.tools.Order;
import de.mein.auth.tools.WaitLock;
import de.mein.drive.DriveSyncListener;
import de.mein.drive.data.Commit;
import de.mein.drive.data.CommitAnswer;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.data.conflict.ConflictSolver;
import de.mein.drive.jobs.CommitJob;
import de.mein.drive.jobs.SyncClientJob;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.sql.*;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.drive.tasks.SyncTask;
import de.mein.sql.SqlQueriesException;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.util.*;

/**
 * Created by xor on 10/27/16.
 */
public class ClientSyncHandler extends SyncHandler {

    private DriveSyncListener syncListener;
    private MeinDriveClientService meinDriveService;
    private Map<String, ConflictSolver> conflictSolverMap = new HashMap<>();

    public void setSyncListener(DriveSyncListener syncListener) {
        this.syncListener = syncListener;
    }


    public ClientSyncHandler(MeinAuthService meinAuthService, MeinDriveClientService meinDriveService) {
        super(meinAuthService, meinDriveService);
        this.meinDriveService = meinDriveService;
    }


    /**
     * call this if you are the receiver
     */
    private void setupTransfer() {
        try {
            N.sqlResource(fsDao.getNonSyncedFilesResource(), resource -> {
                FsFile fsFile = resource.getNext();
                while (fsFile != null) {
                    //todo debug
                    if (fsFile.getContentHash().v().equals("238810397cd86edae7957bca350098bc"))
                        System.out.println("TransferDao.insert.debugmic3n0fv");
                    TransferDetails transfer = new TransferDetails();
                    transfer.getServiceUuid().v(driveSettings.getClientSettings().getServerServiceUuid());
                    transfer.getCertId().v(driveSettings.getClientSettings().getServerCertId());
                    transfer.getHash().v(fsFile.getContentHash());
                    transfer.getSize().v(fsFile.getSize());
                    // this might fail due to unique constraint violation.
                    // happens if you created two identical files at once.
                    // no problem here
                    try {
                        transferManager.createTransfer(transfer);
                    } catch (SqlQueriesException e) {
                        System.err.println("ClientSyncHandler.setupTransfer.exception: " + e.getMessage());
                    }
                    fsFile = resource.getNext();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("ClientSyncHandler.setupTransfer.done: starting...");
        transferManager.research();
    }

    /**
     * Sends the StageSet to the server and updates it with the FsIds provided by the server.
     * blocks until server has answered or the attempt failed.
     *
     * @param stageSetId
     * @throws SqlQueriesException
     * @throws InterruptedException
     */
    @SuppressWarnings("unchecked")
    public void syncWithServer(Long stageSetId) throws SqlQueriesException, InterruptedException {
        // stage is complete. first lock on FS
        FsDao fsDao = driveDatabaseManager.getFsDao();
        StageDao stageDao = driveDatabaseManager.getStageDao();
        WaitLock waitLock = new WaitLock();
//        fsDao.unlockRead();
        //fsDao.lockWrite();
        stageDao.lockRead();

        if (stageDao.stageSetHasContent(stageSetId)) {
            waitLock.lock();
            //all other stages we can find at this point are complete/valid and wait at this point.
            //todo conflict checking goes here - has to block

            Promise<MeinValidationProcess, Exception, Void> connectedPromise = meinAuthService.connect(driveSettings.getClientSettings().getServerCertId());
            connectedPromise.done(mvp -> N.r(() -> {
                Commit commit = new Commit()
                        .setStages(driveDatabaseManager.getStageDao().getStagesByStageSetAsList(stageSetId))
                        .setServiceUuid(meinDriveService.getUuid())
                        .setBasedOnVersion(driveDatabaseManager.getLatestVersion());
                mvp.request(driveSettings.getClientSettings().getServerServiceUuid(), DriveStrings.INTENT_COMMIT, commit).done(result -> N.r(() -> {
                    //fsDao.lockWrite();
                    CommitAnswer answer = (CommitAnswer) result;
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
                    waitLock.unlock();
                    //fsDao.unlockWrite();
                }))
                        .fail(result -> {
                            if (result instanceof TooOldVersionException) {
                                System.out.println("ClientSyncHandler.syncWithServer");
                                N.r(() -> {
                                    meinDriveService.addJob(new SyncClientJob());
                                });
                            }
                            waitLock.unlock();
                        });
            }));
            connectedPromise.fail(ex -> {
                // todo server did not commit. it probably had a local change. have to solve it here
                System.err.println("MeinDriveClientService.startIndexer.could not connect :( due to: " + ex.getMessage());
                // fsDao.unlockWrite();
                stageDao.unlockRead();
                waitLock.unlock();
                meinDriveService.onSyncFailed();
            });
        } else {
            stageDao.deleteStageSet(stageSetId);
            stageDao.unlockRead();
        }
        waitLock.lock();
    }

    /**
     * Merges all staged StageSets (from file system) and checks the result for conflicts
     * with the stage from the server (if it exists).
     * If conflicts are resolved, server StageSet is committed.
     * if a single staged StageSet from file system remains it is send to the server
     * and a new CommitJob added to the MeinDriveServices working queue.
     */
    public void commitJob() {
        System.out.println("ClientSyncHandler.commitJob");
        try {
            // first wait until every staging stuff is finished.
            fsDao.lockRead();

            List<StageSet> stagedFromFs = stageDao.getStagedStageSetsFromFS();
            System.out.println("ClientSyncHandler.commitJob");

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
            if (updateSets.size() == 1 && stagedFromFs.size() == 1) {
                // method should create a new CommitJob with conflict solving details
                handleConflict(updateSets.get(0), stagedFromFs.get(0));
                setupTransfer();
                transferManager.research();
                System.out.println("ClientSyncHandler.commitJob.fwq3j0");
                return;
            } else if (stagedFromFs.size() == 1) {
                //method should create a new CommitJob ? method blocks
                syncWithServer(stagedFromFs.get(0).getId().v());
                return;
            } else if (stagedFromFs.size() > 1) {
                // merge again
                meinDriveService.addJob(new CommitJob());
                return;
            }

            if (updateSets.size() > 1)
                System.err.println("ClientSyncHandler.commitJob.something went seriously wrong");

            // lets assume that everything went fine!
            boolean hasCommitted = false;
            for (StageSet stageSet : updateSets) {
                if (stageDao.stageSetHasContent(stageSet.getId().v())) {
                    commitStage(stageSet.getId().v(), false);
                    setupTransfer();
                    hasCommitted = true;
                } else
                    stageDao.deleteStageSet(stageSet.getId().v());
            }
            // new job in case we have solved conflicts in this run.
            // they must be committed to the server
            if (hasCommitted)
                meinDriveService.addJob(new CommitJob());
            // we are done here
            meinDriveService.onSyncDone();
        } catch (Exception e) {
            e.printStackTrace();
            meinDriveService.onSyncFailed();
        } finally {
            fsDao.unlockWrite();
        }

    }


    /**
     * check whether or not there are any conflicts between stuff that happend on this computer and stuff
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
                if (serverStageSet.getId().v() == 8 && stagedFromFs.getId().v() == 11)
                    System.out.println("ClientSyncHandler.handleConflict.debung0vbgiw435g");
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
                conflictSolverMap.put(conflictSolver.getIdentifier(), conflictSolver);
                conflictSolver.setSolving(true);
                meinDriveService.onConflicts(conflictSolver);
            } else {
                // todo FsDir hash conflicts
                conflictSolver.directoryStuff();
                conflictSolver.cleanup();
                this.commitStage(serverStageSet.getId().v());
                setupTransfer();
                transferManager.research();
                Long mergedId = conflictSolver.getMergeStageSet().getId().v();
                this.minimizeStage(mergedId);
                if (stageDao.stageSetHasContent(mergedId))
                    meinDriveService.addJob(new CommitJob());
            }
        }
    }

    private void minimizeStage(Long stageSetId) throws SqlQueriesException {
        N.sqlResource(stageDao.getStagesResource(stageSetId), stages -> {
            Stage stage = stages.getNext();
            while (stage != null) {
                Long fsId = stage.getFsId();
                if (fsId != null) {
                    FsEntry fsEntry = fsDao.getGenericById(fsId);
                    if (stage.getDeleted() || (fsEntry != null && fsEntry.getContentHash().v().equals(stage.getContentHash()))) {
                        stageDao.deleteStageById(stage.getId());
                    }
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
        System.out.println("ClientSyncHandler.mergeStageSets L: " + lStageSet.getId().v() + " R: " + rStageSet.getId().v());
        StageSet mStageSet = stageDao.createStageSet(DriveStrings.STAGESET_SOURCE_MERGED, null, null);
        final Long mStageSetId = mStageSet.getId().v();
        SyncStageMerger merger = new SyncStageMerger(lStageSet.getId().v(), rStageSet.getId().v()) {
            private Order order = new Order();
            private Map<Long, Long> idMapRight = new HashMap<>();
            private Map<Long, Long> idMapLeft = new HashMap<>();

            @Override
            public void stuffFound(Stage left, Stage right, File lFile, File rFile) throws SqlQueriesException {
                if (left != null) {
                    if (right != null) {
                        Stage stage = new Stage().setOrder(order.ord()).setStageSet(mStageSetId);
                        stage.mergeValuesFrom(right);
                        // do not forget to relate to the parent!
                        if (idMapRight.containsKey(right.getParentId()))
                            stage.setParentId(idMapRight.get(right.getParentId()));
                        stageDao.insert(stage);
                        idMapRight.put(right.getId(), stage.getId());
                        stageDao.flagMerged(right.getId(), true);
                    } else {
                        // only merge if file exists
                        File fLeft = stageDao.getFileByStage(left);
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
                                File rParentFile = stageDao.getFileByStage(rParent);
                                Stage lParent = stageDao.getStageByPath(mStageSetId, rParentFile);
                                if (lParent != null) {
                                    stage.setParentId(lParent.getId());
                                } else {
                                    System.err.println("ClientSyncHandler.stuffFound.8c8h38h9");
                                }
                                stageDao.insert(stage);
                            }
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
                File lFile = stageDao.getFileByStage(lStage);
                Stage rStage = stageDao.getStageByPath(rStageSet.getId().v(), lFile);
                if (conflictSolver != null)
                    conflictSolver.solve(lStage, rStage);
                else {
                    File rFile = (rStage != null) ? new File(lFile.getAbsolutePath()) : null;
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
                    File rFile = stageDao.getFileByStage(rStage);
                    merger.stuffFound(null, rStage, null, rFile);
                }
                rStage = rStages.getNext();
            }
        });
    }


    /**
     * pulls StageSet from the Server
     *
     * @throws SqlQueriesException
     * @throws InterruptedException
     */
    public void syncThisClient() throws SqlQueriesException, InterruptedException {
        runner.runTry(() -> {
            stageDao.deleteServerStageSets();
        });
        Certificate serverCert = meinAuthService.getCertificateManager().getTrustedCertificateById(driveSettings.getClientSettings().getServerCertId());
        Promise<MeinValidationProcess, Exception, Void> connected = meinAuthService.connect(serverCert.getId().v(), serverCert.getAddress().v(), serverCert.getPort().v(), serverCert.getCertDeliveryPort().v(), false);
        connected.done(mvp -> runner.runTry(() -> {
            long version = driveDatabaseManager.getDriveSettings().getLastSyncedVersion();
            StageSet stageSet = stageDao.createStageSet(DriveStrings.STAGESET_SOURCE_SERVER, null, null);
            Request<SyncTask> request = mvp.request(driveSettings.getClientSettings().getServerServiceUuid(), DriveStrings.INTENT_SYNC, new SyncTask().setVersion(version));
            request.done(syncTask -> runner.runTry(() -> {
                syncTask.setStageSet(stageSet);
                syncTask.setSourceCertId(driveSettings.getClientSettings().getServerCertId());
                syncTask.setSourceServiceUuid(driveSettings.getClientSettings().getServerServiceUuid());
                Promise<Long, Void, Void> promise = this.sync2Stage(syncTask);
                promise.done(nil -> runner.runTry(() -> {
                    meinDriveService.addJob(new CommitJob());
                    if (syncListener != null)
                        syncListener.onSyncDone();
                })).fail(result -> {
                            System.err.println("ClientSyncHandler.syncThisClient.j99f49459f54");
                            N.r(() -> stageDao.deleteStageSet(stageSet.getId().v()));
                        }
                );
            })).fail(exc -> {
                System.out.println("ClientSyncHandler.syncThisClient.EXCEPTION: " + exc);
            });
        }));
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
        List<GenericFSEntry> entries = syncTask.getResult();
        if (entries == null) {
            finished.resolve(null);
            return finished;
        }
        StageSet stageSet = syncTask.getStageSet();
        syncTask.setStageSetId(stageSet.getId().v());
        // stage first
        for (GenericFSEntry genericFSEntry : entries) {
            Stage stage = GenericFSEntry.generic2Stage(genericFSEntry, stageSet.getId().v());
            stage.setOrder(order.ord());
            //todo duplicate
            if (stage.getFsId() != null && !stage.getIsDirectory() && fsDao.hasId(stage.getFsId())) {
                FsEntry fsEntry = fsDao.getFile(stage.getFsId());
                stage.setSynced(fsEntry.getSynced().v());
            } else {
                stage.setSynced(false);
            }
//            if (!stage.getIsDirectory())
//                stage.setSynced(false);
            insertWithParentId(entryIdStageIdMap, genericFSEntry, stage);
        }
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
                    System.out.println("ClientSyncHandler.syncThisClient.SOMETHING.DELETED?");
                    fsDirIdsToRetrieve.put(fsDirectory.getId().v(), fsDirectory);
                }
            }
        }
        if (fsDirIdsToRetrieve.size() > 0) {
            Promise<List<FsDirectory>, Exception, Void> promise = meinDriveService.requestDirectoriesByIds(fsDirIdsToRetrieve.keySet(), driveSettings.getClientSettings().getServerCertId(), driveSettings.getClientSettings().getServerServiceUuid());
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
                            //todo dubplicate 2
                            if (stage.getFsId() != null && !stage.getIsDirectory() && fsDao.hasId(stage.getFsId())) {
                                FsEntry fsEntry = fsDao.getFile(stage.getFsId());
                                stage.setSynced(fsEntry.getSynced().v());
                            } else {
                                stage.setSynced(false);
                            }
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
