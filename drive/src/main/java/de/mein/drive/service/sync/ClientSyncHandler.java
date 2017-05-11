package de.mein.drive.service.sync;

import de.mein.auth.data.db.Certificate;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.auth.tools.Order;
import de.mein.drive.DriveSyncListener;
import de.mein.drive.data.Commit;
import de.mein.drive.data.CommitAnswer;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.jobs.CommitJob;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.sql.*;
import de.mein.drive.sql.dao.FsDao;
import de.mein.drive.sql.dao.StageDao;
import de.mein.drive.tasks.SyncTask;
import de.mein.sql.ISQLResource;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by xor on 10/27/16.
 */
public class ClientSyncHandler extends SyncHandler {

    private DriveSyncListener syncListener;
    private MeinDriveClientService meinDriveService;

    public void setSyncListener(DriveSyncListener syncListener) {
        this.syncListener = syncListener;
    }


    public ClientSyncHandler(MeinAuthService meinAuthService, MeinDriveClientService meinDriveService) {
        super(meinAuthService, meinDriveService);
        this.meinDriveService = meinDriveService;
    }

    public void syncThisClient() throws SqlQueriesException, InterruptedException {
        Certificate serverCert = meinAuthService.getCertificateManager().getTrustedCertificateById(driveSettings.getClientSettings().getServerCertId());
        Promise<MeinValidationProcess, Exception, Void> connected = meinAuthService.connect(serverCert.getId().v(), serverCert.getAddress().v(), serverCert.getPort().v(), serverCert.getCertDeliveryPort().v(), false);
        connected.done(mvp -> runner.runTry(() -> {
            long version = driveDatabaseManager.getDriveSettings().getLastSyncedVersion();
            Request<SyncTask> request = mvp.request(driveSettings.getClientSettings().getServerServiceUuid(), DriveStrings.INTENT_SYNC, new SyncTask().setVersion(version));
            request.done(syncTask -> runner.runTry(() -> {
                syncTask.setSourceCertId(driveSettings.getClientSettings().getServerCertId());
                syncTask.setSourceServiceUuid(driveSettings.getClientSettings().getServerServiceUuid());
                //driveDatabaseManager.lockWrite();
                Promise<Long, Void, Void> promise = this.sync(syncTask);
                promise.done(nil -> runner.runTry(() -> {
                    //driveDatabaseManager.unlockWrite();
                    meinDriveService.addJob(new CommitJob());
                    if (syncListener != null)
                        syncListener.onSyncDone();
                }));

            }));
        }));
    }


    private Stage generic2Stage(GenericFSEntry genericFSEntry, Long stageSetId) {
        Stage stage = new Stage()
                .setFsId(genericFSEntry.getId().v())
                .setFsParentId(genericFSEntry.getParentId().v())
                .setName(genericFSEntry.getName().v())
                .setIsDirectory(genericFSEntry.getIsDirectory().v())
                .setContentHash(genericFSEntry.getContentHash().v())
                .setStageSet(stageSetId)
                .setSize(genericFSEntry.getSize().v())
                .setDeleted(false);
        return stage;
    }

    /**
     * delta goes in here
     *
     * @param syncTask contains delta
     * @return stageSetId in Promise
     * @throws SqlQueriesException
     * @throws InterruptedException
     */
    public Promise<Long, Void, Void> sync(SyncTask syncTask) throws SqlQueriesException, InterruptedException {
        DeferredObject<Void, Void, Void> communicationDone = new DeferredObject<>();
        DeferredObject<Long, Void, Void> finished = new DeferredObject<>();
        Order order = new Order();
        List<GenericFSEntry> entries = syncTask.getResult();
        if (entries == null) {
            finished.resolve(null);
            return finished;
        }
        StageSet stageSet = stageDao.createStageSet(DriveStrings.STAGESET_TYPE_FROM_SERVER, null, null);
        syncTask.setStageSetId(stageSet.getId().v());
        // stage first
        for (GenericFSEntry genericFSEntry : entries) {
            Stage stage = generic2Stage(genericFSEntry, stageSet.getId().v());
            stage.setOrder(order.ord());
            stageDao.insert(stage);
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
                    Set<Long> stillExistingIds = new HashSet<Long>();
                    directory.getFiles().forEach(fsFile -> stillExistingIds.add(fsFile.getId().v()));
                    directory.getSubDirectories().forEach(fsDirectory -> stillExistingIds.add(fsDirectory.getId().v()));

                    List<GenericFSEntry> fsDirs = fsDao.getContentByFsDirectory(directory.getId().v());
                    for (GenericFSEntry genSub : fsDirs) {
                        if (!stillExistingIds.contains(genSub.getId().v())) {
                            // genSub shall be deleted
                            Stage stage = generic2Stage(genSub, stageSet.getId().v());
                            stage.setDeleted(true);
                            stage.setOrder(order.ord());
                            stageDao.insert(stage);
                        }
                    }
                }
                setupTransfer(syncTask);
                //stageDao.commitStage(stageSet.getId().v());
                communicationDone.resolve(null);
            }));
        } else {
            setupTransfer(syncTask);
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

    /**
     * call this if you are the receiver
     *
     * @param syncTask
     */
    public void setupTransfer(SyncTask syncTask) {
        Long stageSetId = syncTask.getStageSetId();
        ISQLResource<de.mein.drive.sql.Stage> resource = null;
        try {
            resource = stageDao.getFilesAsResource(stageSetId);
            Stage stage = resource.getNext();
            while (stage != null) {
                TransferDetails transfer = new TransferDetails();
                transfer.getServiceUuid().v(syncTask.getSourceServiceUuid());
                transfer.getCertId().v(syncTask.getSourceCertId());
                transfer.getHash().v(stage.getContentHash());
                transfer.getSize().v(stage.getSize());
                // this might fail due to unique constraint violation.
                // happens if you created two identical files at once.
                // no problem here
                try {
                    transferManager.createTransfer(transfer);
                } catch (SqlQueriesException e) {
                    System.err.println("ClientSyncHandler.setupTransfer.exception: " + e.getMessage());
                }
                stage = resource.getNext();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (resource != null)
                try {
                    resource.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
        }
        System.out.println("ClientSyncHandler.setupTransfer.done: starting...");
        transferManager.research();
    }

    public void syncWithServer(Long stageSetId) throws SqlQueriesException, InterruptedException {
        // stage is complete. first lock on FS
        FsDao fsDao = driveDatabaseManager.getFsDao();
        StageDao stageDao = driveDatabaseManager.getStageDao();
        fsDao.unlockRead();
        //fsDao.lockWrite();
        stageDao.lockRead();

        if (stageDao.stageSetHasContent(stageSetId)) {

            //all other stages we can find at this point are complete/valid and wait at this point.
            //todo conflict checking goes here - has to block

            Promise<MeinValidationProcess, Exception, Void> connectedPromise = meinAuthService.connect(driveSettings.getClientSettings().getServerCertId());
            connectedPromise.done(mvp -> N.r(() -> {
                Commit commit = new Commit().setStages(driveDatabaseManager.getStageDao().getStagesByStageSetAsList(stageSetId)).setServiceUuid(meinDriveService.getUuid());
                mvp.request(driveSettings.getClientSettings().getServerServiceUuid(), DriveStrings.INTENT_COMMIT, commit).done(result -> N.r(() -> {
                    //fsDao.lockWrite();
                    CommitAnswer answer = (CommitAnswer) result;
                    ISQLResource<Stage> stages = stageDao.getStagesByStageSet(stageSetId);
                    Stage stage = stages.getNext();
                    while (stage != null) {
                        Long fsId = answer.getStageIdFsIdMap().get(stage.getId());
                        if (fsId != null) {
                            stage.setFsId(fsId);
                            if (stage.getParentId() != null) {
                                Long fsParentId = answer.getStageIdFsIdMap().get(stage.getParentId());
                                if (fsParentId != null)
                                    stage.setFsParentId(fsParentId);
                            }
                            stageDao.update(stage);
                        }
                        stage = stages.getNext();
                    }
                    StageSet stageSet = stageDao.getStageSetById(stageSetId);
                    stageSet.setStatus(DriveStrings.STAGESET_STATUS_SERVER_COMMITED);
                    stageDao.updateStageSet(stageSet);
//                    addJob(new CommitJob());
                    //syncHandler.commitStage(stageSetId, false);
                    //fsDao.unlockWrite();
                }));

            }));
            System.err.println("MeinDriveClientService.initDatabase");
            connectedPromise.fail(ex -> {
                // todo server did not commit. it probably had a local change. have to solve it here
                System.err.println("MeinDriveClientService.initDatabase.could not connect :( due to: " + ex.getMessage());
                fsDao.unlockWrite();
                stageDao.unlockRead();
            });
        } else {
            stageDao.deleteStageSet(stageSetId);
//                fsDao.unlockWrite();
            stageDao.unlockRead();
        }
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
            fsDao.lockWrite();

            List<StageSet> stagedStageSets = stageDao.getStagedStageSetsFromFS();
            System.out.println("ClientSyncHandler.commitJob");

            // first: merge everything
            mergeStageSets(stagedStageSets);
            // now lets look for possible conflicts with stuff from the server
            List<StageSet> stagesSetsFromServer = stageDao.getStagedStageSetsFromServer();
            if (stagesSetsFromServer.size() == 1) {
                checkConflicts(stagesSetsFromServer.get(0), stagedStageSets);
            }

            if (stagesSetsFromServer.size() > 1) {
                System.err.println("k4i9jfw4f3o0");
            }
            // lets assume that everything went fine!
            for (StageSet stageSet : stagesSetsFromServer) {
                commitStage(stageSet.getId().v(), false);
            }
            // we are done here
            meinDriveService.getSyncListener().onSyncDone();
        } catch (SqlQueriesException e) {
            e.printStackTrace();
        } finally {
            fsDao.unlockWrite();
        }
    }


    /**
     * check whether or not there are any conflicts between stuff that happend on this computer and stuff
     * that happened on the server. this will block until all conflicts are resolved.
     *
     * @param serverStageSet
     * @param stagedStageSets
     */
    private void checkConflicts(StageSet serverStageSet, List<StageSet> stagedStageSets) {
        System.out.println("ClientSyncHandler.checkConflicts.NOT:IMPLEMNETED:YET");
    }

    /**
     * merges two {@link StageSet}s to a new one
     *
     * @param stageSets
     * @throws SqlQueriesException
     */
    private void mergeStageSets(List<StageSet> stageSets) throws SqlQueriesException {
        System.out.println("ClientSyncHandler.mergeStageSets");
        if (stageSets.size() > 2)
            System.err.println("ClientSyncHandler.mergeStageSets.TOO MANY!!!1");
        if (stageSets.size() <= 1)
            return;
        StageSet lStageSet = stageSets.get(0);
        StageSet rStageSet = stageSets.get(1);
        StageSet mStageSet = stageDao.createStageSet(DriveStrings.STAGESET_TYPE_MERGED, null, null);
        final Long mStageSetId = mStageSet.getId().v();
        SyncStagesComparator comparator = new SyncStagesComparator(lStageSet.getId().v(), rStageSet.getId().v()) {
            private Order order = new Order();
            private Map<Long, Long> idMapRight = new HashMap<>();
            private Map<Long, Long> idMapLeft = new HashMap<>();

            @Override
            public void stuffFound(Stage left, Stage right) throws SqlQueriesException {
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
                        if (fLeft.exists()) {
                            Stage stage = new Stage().setOrder(order.ord()).setStageSet(mStageSetId);
                            stage.mergeValuesFrom(left);
                            if (idMapLeft.containsKey(left.getParentId()))
                                stage.setParentId(idMapLeft.get(left.getParentId()));
                            stageDao.insert(stage);
                            idMapLeft.put(left.getId(), stage.getId());
                        }
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
        iterateStageSets(lStageSet, rStageSet, comparator);
        stageDao.deleteStageSet(rStageSet.getId().v());
        stageDao.deleteStageSet(lStageSet.getId().v());
        stageDao.updateStageSet(mStageSet.setStatus(DriveStrings.STAGESET_STATUS_STAGED).setType(DriveStrings.STAGESET_TYPE_FS));
    }

    /**
     * iterates over the left {@link StageSet} first and finds
     * relating entries in the right {@link StageSet} and flags everything it finds (in the right StageSet).
     * Afterwards it iterates over all non flagged {@link Stage}s of the right {@link StageSet}.
     * Iteration is in order of the Stages insertion.
     *
     * @param lStageSet
     * @param rStageSet
     * @param comparator
     * @throws SqlQueriesException
     */
    private void iterateStageSets(StageSet lStageSet, StageSet rStageSet, SyncStagesComparator comparator) throws SqlQueriesException {
        ISQLResource<Stage> lStages = stageDao.getStagesResource(lStageSet.getId().v());
        Stage lStage = lStages.getNext();
        while (lStage != null) {
            File file = stageDao.getFileByStage(lStage);
            Stage rStage = stageDao.getStageByPath(rStageSet.getId().v(), file);
            comparator.stuffFound(lStage, rStage);
            lStage = lStages.getNext();
        }
        ISQLResource<Stage> rStages = stageDao.getNotFoundStagesResource(rStageSet.getId().v());
        Stage rStage = rStages.getNext();
        while (rStage != null) {
            comparator.stuffFound(null, rStage);
            rStage = rStages.getNext();
        }
    }

}
