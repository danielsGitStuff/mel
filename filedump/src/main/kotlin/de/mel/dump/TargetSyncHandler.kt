package de.mel.dump

import de.mel.Lok
import de.mel.auth.data.access.CertificateManager
import de.mel.auth.service.MelAuthService
import de.mel.auth.socket.process.`val`.Request
import de.mel.auth.tools.MapWrap
import de.mel.auth.tools.N
import de.mel.auth.tools.lock2.P
import de.mel.auth.tools.lock2.BunchOfLocks
import de.mel.filesync.data.Commit
import de.mel.filesync.service.sync.ServerSyncHandler
import de.mel.filesync.sql.DbTransferDetails
import de.mel.filesync.sql.FsEntry
import de.mel.filesync.sql.GenericFSEntry
import de.mel.filesync.sql.Stage
import de.mel.sql.ISQLResource
import java.time.LocalDateTime
import java.time.ZoneOffset

class TargetSyncHandler(melAuthService: MelAuthService, targetService: TargetService) : ServerSyncHandler(melAuthService, targetService) {
    override fun handleCommit(request: Request<*>?) {
        val commit = request!!.payload as Commit
        val warden: BunchOfLocks = P.confine(fsDao)
        try {
            executeCommit(request, commit, warden)
        } finally {
            warden.end()
        }
        transferManager.research()
        Lok.debug("MelDriveServerService.handleCommit")
    }

    private var booted = false

    override fun commitStage(stageSetId: Long, bunchOfLocks: BunchOfLocks, stageIdFsIdMap: MutableMap<Long, Long>) {
        val stageSet = stageDao.getStageSetById(stageSetId)
        // just let the first fs commit through, it is the boot index stage set
        if (stageSet.fromFs() && !booted) {
            booted = true
            super.commitStage(stageSetId, bunchOfLocks, stageIdFsIdMap)
            return
        }
        bunchOfLocks.run {
            val localVersion = fsDao.latestVersion + 1
            // put new stuff in place
            N.readSqlResource(stageDao.getNotDeletedStagesByStageSet(stageSetId)) { sqlResource: ISQLResource<Stage>, stage: Stage ->
                val fsParentId = stage.fsParentId ?: stageIdFsIdMap?.get(stage.parentId)
                val fsEntry = GenericFSEntry()
                fsEntry.isDirectory.v(stage.isDirectory)
                fsEntry.name.v(stage.name)
                fsEntry.contentHash.v(stage.contentHash)
                fsEntry.parentId.v(fsParentId)
                fsEntry.created.v(stage.created)
                fsEntry.version.v(localVersion)
                fsEntry.size.v(stage.size)
                fsEntry.synced.v(stage.isDirectory)

                if (fsParentId == null) {
                    Lok.error("this is either the root dir or an error: ${stage.name}")
                } else {
                    val existing = fsDao.getGenericChildByName(fsParentId, stage.name)
                    if (existing == null) {
                        fsDao.insert(fsEntry)
                    } else {
                        resolveConflict(stage, existing, fsEntry, stageIdFsIdMap)
                    }
                    stageIdFsIdMap.set(stage.id, fsEntry.id.v())
                    createDirs(fileSyncDatabaseManager.fileSyncSettings.rootDirectory, fsEntry)
                    // transfer if file
                    if (!stage.isDirectory) {
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
                }
            }
            fileSyncDatabaseManager.updateVersion()
            stageDao.deleteStageSet(stageSetId)
            transferManager.stop()
            transferManager.removeUnnecessaryTransfers()
            transferManager.start()
            transferManager.research()
        }
    }

    /**
     * find an appropriate file name for the new file.
     * new name be like: FILE_NAME[.CREATED][.FS_ID][.RANDOM_UUID].FILE_EXT
     */
    private fun resolveConflict(stage: Stage, existing: GenericFSEntry, fsEntry: FsEntry, stageIdFsIdMap: Map<Long, Long>?) {
        val baseName = existing.name.v()

        if (baseName == stage.name && stage.isDirectory && existing.isDirectory.v())
            return

        // insert with tmp name. this gives us an fs id
        val tmpName = "$baseName.${CertificateManager.randomUUID()}"
        fsEntry.name.v(tmpName)
        fsDao.insert(fsEntry)

        val extension = N.result {
            val lastDotIndex = baseName.lastIndexOf('.')
            if (lastDotIndex < 1)
                ""
            else
                baseName.drop(lastDotIndex)
        }
        val name = baseName.dropLast(extension.length)
        val ldt = LocalDateTime.ofEpochSecond(stage.created/1000, 0, ZoneOffset.UTC)
        val created = "${ldt.year}.${ldt.monthValue}.${ldt.dayOfMonth}.${ldt.hour}.${ldt.minute}.${ldt.second}"
        var newName = "${name}_$created$extension"
        if (fsDao.getGenericChildByName(existing.parentId.v(), newName) != null)
            newName = "${name}_${created}_${fsEntry.id.v()}$extension"
        // enough is enough! just try random UUIDs until one is not taken.
        while (fsDao.getGenericChildByName(existing.parentId.v(), newName) != null)
            newName = "${name}_${created}_${fsEntry.id.v()}_${CertificateManager.randomUUID()}$extension"
        fsEntry.name.v(newName)
        fsDao.updateName(fsEntry.id.v(), newName)
    }
}