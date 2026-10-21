package de.mel.dump

import de.mel.Lok
import de.mel.auth.file.AbstractFile
import de.mel.auth.service.MelAuthService
import de.mel.auth.socket.process.`val`.Request
import de.mel.auth.tools.N
import de.mel.auth.tools.lock2.BunchOfLocks
import de.mel.auth.tools.lock2.P
import de.mel.filesync.data.Commit
import de.mel.filesync.data.CommitAnswer
import de.mel.filesync.data.FileSyncStrings
import de.mel.filesync.service.sync.ServerSyncHandler
import de.mel.filesync.sql.DbTransferDetails
import de.mel.filesync.sql.GenericFSEntry
import de.mel.filesync.sql.Stage
import de.mel.sql.ISQLResource
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * The target of a dump: takes what the sources offer and keeps all of it.
 * <ul>
 * <li>what counts is the content hash. Content that is stored already is neither transferred nor stored again. Another name for it is kept as a reference if the file system
 * can have those, else it is left out ([TargetService.createFsConvenience])</li>
 * <li>the folder structure of the source is kept as far as it is free. A name that is taken by other content gets another name</li>
 * <li>nothing is ever deleted or overwritten: files deleted at the source stay, changed files are new content</li>
 * <li>the ids of the source's fs table mean nothing here, and the target does not tell the source its own. The commit is read by path and name</li>
 * </ul>
 * What is on the target's own disk (the first index, files someone put there) is a normal fs stage set and committed as such.
 */
class TargetSyncHandler(melAuthService: MelAuthService, targetService: TargetService) : ServerSyncHandler(melAuthService, targetService) {
    /**
     * No check of the version the source based its commit on: sources do not follow the target's versions, they only add to it.
     */
    override fun handleCommit(request: Request<*>?) {
        val commit = request!!.payload as Commit
        val warden: BunchOfLocks = P.confine(fsDao)
        try {
            executeCommit(request, commit, warden)
        } finally {
            warden.end()
        }
        transferManager.research()
        Lok.debug("TargetSyncHandler.handleCommit")
    }

    /**
     * Stages what the source sent and commits it. The answer holds no fs ids.
     */
    override fun executeCommit(request: Request<*>, commit: Commit, bunchOfLocks: BunchOfLocks) {
        val stageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_CLIENT, request.partnerCertificate.id.v(), commit.serviceUuid, null, commit.basedOnVersion)
        val iterator = commit.iterator()
        while (iterator.hasNext()) {
            val stage = iterator.next()
            stage.setStageSet(stageSet.id.v())
            // all of these point into the source's tables
            stage.parentIdPair.nul()
            stage.fsIdPair.nul()
            stage.fsParentIdPair.nul()
            stageDao.insert(stage)
        }
        commit.cleanUp()
        try {
            commitStage(stageSet.id.v(), bunchOfLocks, HashMap())
        } catch (e: Exception) {
            Lok.error("TargetSyncHandler: could not commit: $e")
            request.resolveError(e)
            return
        }
        @Suppress("UNCHECKED_CAST")
        (request as Request<CommitAnswer>).resolveOk(CommitAnswer().setStageIdFsIdMap(HashMap()))
    }

    override fun commitStage(stageSetId: Long, bunchOfLocks: BunchOfLocks, stageIdFsIdMap: MutableMap<Long, Long>?) {
        val stageSet = stageDao.getStageSetById(stageSetId)
        if (stageSet.fromFs()) {
            super.commitStage(stageSetId, bunchOfLocks, stageIdFsIdMap)
            return
        }
        bunchOfLocks.run {
            val localVersion = fsDao.latestVersion + 1
            val rootPath = fileSyncDatabaseManager.fileSyncSettings.rootDirectory.path
            N.readSqlResource(stageDao.getNotDeletedStagesByStageSet(stageSetId)) { _: ISQLResource<Stage>, stage: Stage ->
                // directories come with their files, deleted things stay
                if (!stage.isDirectory) {
                    storeFile(stage, stageSet.originCertId.v(), stageSet.originServiceUuid.v(), localVersion, rootPath)
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

    private fun storeFile(stage: Stage, certId: Long?, serviceUuid: String?, localVersion: Long, rootPath: String) {
        val hash = stage.contentHash
        if (hash == null) {
            Lok.error("TargetSyncHandler: no hash for ${stage.path}${stage.name}, not taken")
            return
        }
        var parent = fsDao.getGenericById(fsDao.rootDirectory.id.v())
        for (segment in stage.path.split("/").filter { it.isNotEmpty() })
            parent = directory(parent, segment, localVersion, rootPath)
        // the same file again (the source sends what it did not send before, not what it did): nothing to do
        val sameName = fsDao.getGenericChildByName(parent.id.v(), stage.name)
        if (sameName != null && !sameName.isDirectory.v() && hash == sameName.contentHash.v())
            return
        // The content may be stored or on its way already. The entry is made anyway, and the file distribution decides: with the content at hand it makes a reference
        // for this name (the service's FsConvenience does nothing else) or, if the file system cannot, drops the entry again. A physical copy is never made.
        val name = freeName(parent, stage.name, stage.created)
        val entry = GenericFSEntry()
        entry.isDirectory.v(false)
        entry.name.v(name)
        entry.contentHash.v(hash)
        entry.parentId.v(parent.id.v())
        entry.path.v(childPath(parent))
        entry.depth.v(childPath(parent).count { it == '/' })
        entry.created.v(stage.created)
        entry.modified.v(stage.modified)
        entry.version.v(localVersion)
        entry.size.v(stage.size)
        entry.synced.v(false)
        fsDao.insert(entry)
        val details = DbTransferDetails()
        details.available.v(true)
        details.certId.v(certId)
        details.serviceUuid.v(serviceUuid)
        details.hash.v(hash)
        details.deleted.v(false)
        details.size.v(stage.size)
        N.r { transferManager.createTransfer(details) }
    }

    private fun childPath(parent: GenericFSEntry): String = parent.path.v() + parent.name.v() + "/"

    /**
     * @return the directory called [name] in [parent], made in the fs table and on the disk if it is not there. If something else has this name the directory gets another one.
     */
    private fun directory(parent: GenericFSEntry, name: String, localVersion: Long, rootPath: String): GenericFSEntry {
        var candidate = name
        while (true) {
            val existing = fsDao.getGenericChildByName(parent.id.v(), candidate)
            if (existing == null)
                break
            if (existing.isDirectory.v())
                return existing
            candidate += "_dir"
        }
        val entry = GenericFSEntry()
        entry.isDirectory.v(true)
        entry.name.v(candidate)
        // the target's own index sets the hash of what is in the directory
        entry.contentHash.v("0")
        entry.parentId.v(parent.id.v())
        entry.path.v(childPath(parent))
        entry.depth.v(childPath(parent).count { it == '/' })
        entry.version.v(localVersion)
        entry.synced.v(true)
        fsDao.insert(entry)
        val path = rootPath + entry.path.v() + candidate
        val dir = AbstractFile.instance(path)
        if (!dir.exists()) {
            indexer.ignorePath(path, 1)
            dir.mkdirs()
            indexer.watchDirectory(dir)
        }
        return entry
    }

    /**
     * The name the file has in [parent]: its own if that is free, else with the date it was created and, if need be, a number.
     */
    private fun freeName(parent: GenericFSEntry, name: String, created: Long?): String {
        if (fsDao.getGenericChildByName(parent.id.v(), name) == null)
            return name
        val dot = name.lastIndexOf('.')
        val extension = if (dot < 1) "" else name.substring(dot)
        val base = name.dropLast(extension.length)
        val time = LocalDateTime.ofEpochSecond((created ?: 0L) / 1000, 0, ZoneOffset.UTC)
        val date = "${time.year}.${time.monthValue}.${time.dayOfMonth}.${time.hour}.${time.minute}.${time.second}"
        var candidate = "${base}_$date$extension"
        var number = 1
        while (fsDao.getGenericChildByName(parent.id.v(), candidate) != null)
            candidate = "${base}_${date}_${number++}$extension"
        return candidate
    }
}
