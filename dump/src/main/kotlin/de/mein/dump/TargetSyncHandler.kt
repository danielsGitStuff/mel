package de.mein.dump

import de.mein.Lok
import de.mein.auth.service.MeinAuthService
import de.mein.auth.socket.process.`val`.Request
import de.mein.auth.tools.N
import de.mein.auth.tools.lock.T
import de.mein.auth.tools.lock.Transaction
import de.mein.drive.bash.BashTools
import de.mein.drive.data.Commit
import de.mein.drive.service.sync.ServerSyncHandler
import de.mein.drive.sql.DbTransferDetails
import de.mein.drive.sql.FsDirectory
import de.mein.drive.sql.FsFile
import de.mein.drive.sql.Stage

class TargetSyncHandler(meinAuthService: MeinAuthService, targetService: TargetService) : ServerSyncHandler(meinAuthService, targetService) {
    override fun handleCommit(request: Request<*>?) {
        val commit = request!!.payload as Commit
        val transaction: Transaction<*> = T.lockingTransaction(fsDao)
        try {
            executeCommit(request, commit, transaction)
        } finally {
            transaction.end()
        }
        transferManager.research()
        Lok.debug("MeinDriveServerService.handleCommit")
    }

    override fun commitStage(stageSetId: Long, transaction: Transaction<*>, stageIdFsIdMap: MutableMap<Long, Long>?) {
        transaction.run {
            val stageSet = stageDao.getStageSetById(stageSetId)
            val localVersion = fsDao.latestVersion + 1
            // put new stuff in place
            N.sqlResource(stageDao.getNotDeletedStagesByStageSet(stageSetId)) { stages ->
                var stage: Stage? = stages.next
                while (stage != null) {
                    if (stage.fsId == null) {
                        if (stage.isDirectory!!) {
                            val dir = FsDirectory()
                            dir.version.v(localVersion)
                            dir.contentHash.v(stage.contentHash)
                            dir.name.v(stage.name)
                            dir.modified.v(stage.modified)
                            dir.created.v(stage.created)
                            dir.getiNode().v(stage.getiNode())
                            dir.symLink.v(stage.symLink)
                            var fsParentId: Long? = null
                            if (stage.parentId != null) {
                                fsParentId = stageDao.getStageById(stage.parentId)!!.fsId
                            } else if (stage.fsParentId != null)
                                fsParentId = stage.fsParentId
                            dir.parentId.v(fsParentId)
                            fsDao.insert(dir)
                            if (stageIdFsIdMap != null) {
                                stageIdFsIdMap[stage.id] = dir.id.v()
                            }

                            this.createDirs(driveDatabaseManager.driveSettings.rootDirectory, dir)

                            stage.fsId = dir.id.v()
                        } else {
                            // it is a new file
                            var fsFile: FsFile? = null
                            if (stage.fsId != null)
                                fsFile = fsDao.getFile(stage.fsId)
                            else {
                                fsFile = FsFile()
                                var fsParentId: Long? = null
                                if (stage.parentId != null) {
                                    fsParentId = stageDao.getStageById(stage.parentId)!!.fsId
                                } else if (stage.fsParentId != null)
                                    fsParentId = stage.fsParentId
                                fsFile.parentId.v(fsParentId)
                            }
                            fsFile!!.name.v(stage.name)
                            fsFile.contentHash.v(stage.contentHash)
                            fsFile.version.v(localVersion)
                            fsFile.modified.v(stage.modified)
                            fsFile.created.v(stage.created)
                            fsFile.getiNode().v(stage.getiNode())
                            fsFile.size.v(stage.size)
                            fsFile.symLink.v(stage.symLink)
                            if (stageSet.fromFs()) {
                                fsFile.synced.v(true)
                            } else {
                                fsFile.synced.v(false)
                            }
                            fsDao.insert(fsFile)
                            if (fsFile.isSymlink) {
                                val f = fsDao.getFileByFsFile(driveSettings.rootDirectory, fsFile)
                                BashTools.lnS(f, fsFile.symLink.v())
                            } else if (!stageSet.fromFs() && !stage.isDirectory && !stage.isSymLink) {
                                // this file porobably has to be transferred
                                val details = DbTransferDetails()
                                details.available.v(stage.synced)
                                details.certId.v(stageSet.originCertId)
                                details.serviceUuid.v(stageSet.originServiceUuid)
                                details.hash.v(stage.contentHash)
                                details.deleted.v(false)
                                details.size.v(stage.size)
                                setupTransferAvailable(details, stageSet, stage)
                                N.r { transferManager.createTransfer(details) }
                            }
                            if (stageIdFsIdMap != null) {
                                stageIdFsIdMap[stage.id] = fsFile.id.v()
                            }
                            stage.fsId = fsFile.id.v()
                        }
                    } else { // fs.id is not null
                        if (stage.fsParentId != null && !fsDao.hasId(stage.fsParentId)) {//skip if parent was deleted
                            stage = stages.next
                            continue
                        }
                        if (stage.deleted != null && stage.deleted!! && stage.synced != null && stage.synced!! || stage.isDirectory!! && stage.deleted!!) {
                            //if (stage.getDeleted() != null && stage.getSynced() != null && (stage.getDeleted() && stage.getSynced())) {
                            //todo BUG: 3 Conflict solve dialoge kommen hoch, wenn hier Haltepunkt bei DriveFXTest.complectConflict() drin ist
                            //                            wastebin.deleteFsEntry(stage.getFsId());
                            Lok.debug("debug")
                        } else {
                            val fsEntry = stageDao.stage2FsEntry(stage)
                            if (fsEntry.version.isNull) {
                                Lok.debug("//pe, should not be called")
                                fsEntry.version.v(localVersion)
                            }
                            // TODO inode & co
                            val oldeEntry = fsDao.getGenericById(fsEntry.id.v())
                            // only copy modified & inode if it is not present in the new entry (it came from remote then)
                            if (oldeEntry != null && oldeEntry.isDirectory.v() && fsEntry.isDirectory.v() && fsEntry.modified.isNull) {
                                fsEntry.getiNode().v(oldeEntry.getiNode())
                                fsEntry.modified.v(oldeEntry.modified)
                                fsEntry.created.v(oldeEntry.created)
                            }
                            if (fsEntry.id.v() != null && !fsEntry.isDirectory.v()) {
                                val oldeFsFile = fsDao.getFile(fsEntry.id.v())
                                if (oldeFsFile != null && !stageSet.fromFs() && fsEntry.synced.notNull() && !fsEntry.synced.v()) {
                                    wastebin.deleteFsFile(oldeFsFile)
                                } else {
                                    // delete file. consider that it might be in the same state as the stage
                                    val stageFile = stageDao.getFileByStage(stage)
                                    if (stageFile.exists()) {
                                        val fsBashDetails = BashTools.getFsBashDetails(stageFile)
                                        if (stage.getiNode() == null || stage.modified == null ||
                                                !(fsBashDetails.getiNode() == stage.getiNode() && fsBashDetails.modified == stage.modified)) {
                                            wastebin.deleteUnknown(stageFile)
                                            //                                            stage.setSynced(false);
                                            // we could search more recent stagesets to find some clues here and prevent deleteUnknown().
                                        }
                                        // else: the file is as we want it to be
                                    }
                                }
                            }
                            if (!fsEntry.isDirectory.v() && stage.synced != null && !stage.synced)
                                fsEntry.synced.v(false)
                            // its remote -> not in place
                            if (!stage.isDirectory && !stageSet.fromFs())
                                fsEntry.synced.v(false)
                            else if (stageSet.fromFs()) {
                                fsEntry.synced.v(true)
                            }
                            fsDao.insertOrUpdate(fsEntry)
                            if (stageSet.originCertId.notNull() && !stage.isDirectory && !stage.isSymLink) {
                                val details = DbTransferDetails()
                                details.certId.v(stageSet.originCertId)
                                details.serviceUuid.v(stageSet.originServiceUuid)
                                details.hash.v(stage.contentHash)
                                details.deleted.v(false)
                                details.size.v(stage.size)
                                setupTransferAvailable(details, stageSet, stage)
                                N.r { transferManager.createTransfer(details) }
                            }
                            this.createDirs(driveDatabaseManager.driveSettings.rootDirectory, fsEntry)
                        }
                    }
                    stageDao.update(stage)
                    stage = stages.next
                }
                driveDatabaseManager.updateVersion()
                stageDao.deleteStageSet(stageSetId)
                transferManager.stop()
                transferManager.removeUnnecessaryTransfers()
                transferManager.start()
                transferManager.research()
            }
            wastebin.maintenance()

//            N.readSqlResource(stageDao.getStagesResource(stageSetId)) { sqlResource: ISQLResource<Stage>, stage: Stage ->
//                if (stage.isDirectory) {
//                    if (stage.fsIdPair.isNull()){
//
//                    }
//                } else {
//
//                }
//            }
        }
    }
}