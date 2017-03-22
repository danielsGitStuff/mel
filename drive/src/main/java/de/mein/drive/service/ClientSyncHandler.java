package de.mein.drive.service;

import de.mein.auth.data.db.Certificate;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.socket.process.val.Request;
import de.mein.drive.DriveSyncListener;
import de.mein.drive.data.DriveStrings;
import de.mein.drive.sql.*;
import de.mein.drive.tasks.SyncTask;
import de.mein.sql.ISQLResource;
import de.mein.sql.SqlQueriesException;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.sql.SQLException;
import java.util.*;

/**
 * Created by xor on 10/27/16.
 */
public class ClientSyncHandler extends SyncHandler {

    private DriveSyncListener syncListener;

    public void setSyncListener(DriveSyncListener syncListener) {
        this.syncListener = syncListener;
    }


    public ClientSyncHandler(MeinAuthService meinAuthService, MeinDriveService meinDriveService) {
        super(meinAuthService, meinDriveService);
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
                    this.commitStage(syncTask.getStageSetId());
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
        List<GenericFSEntry> entries = syncTask.getResult();
        if (entries == null) {
            finished.resolve(null);
            return finished;
        }
        StageSet stageSet = stageDao.createStageSet("cameFromServer", null, null);
        syncTask.setStageSetId(stageSet.getId().v());
        // stage first
        for (GenericFSEntry genericFSEntry : entries) {
            Stage stage = generic2Stage(genericFSEntry, stageSet.getId().v());
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
        }finally {
            if (resource!=null)
                try {
                    resource.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
        }
        System.out.println("ClientSyncHandler.setupTransfer.done: starting...");
        transferManager.research();
    }
}
