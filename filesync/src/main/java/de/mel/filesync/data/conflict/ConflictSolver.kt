package de.mel.filesync.data.conflict

import de.mel.auth.tools.N
import de.mel.auth.tools.Order
import de.mel.filesync.data.FileSyncStrings
import de.mel.filesync.sql.FsDirectory
import de.mel.filesync.sql.GenericFSEntry
import de.mel.filesync.sql.Stage
import de.mel.filesync.sql.StageSet
import de.mel.filesync.sql.dao.ConflictDao
import de.mel.sql.ISQLResource
import de.mel.sql.SqlQueriesException
import java.util.*

/**
 * Finds Conflicts according to @see bla
 * todo add link do conflict image
 */
class ConflictSolver(private val conflictDao: ConflictDao, val localStageSet: StageSet, val remoteStageSet: StageSet) {
    var conflictHelperUuid: String? = null
    val stageDao = conflictDao.stageDao
    val fsDao = conflictDao.fsDao
    val basedOnVersion: Long
    val conflictMap = mutableMapOf<String, Conflict>()
    val rootConflictMap = mutableMapOf<String, Conflict>()

    val localStageConflictMap = mutableMapOf<Long, Conflict>()

    val remoteStageConflictMap = mutableMapOf<Long, Conflict>()

    private val rootConflicts = mutableListOf<Conflict>()
    private var mergeStageSet: StageSet
    private var obsoleteStageSet: StageSet
    var order = Order()

    init {
        basedOnVersion = if (localStageSet.basedOnVersion.v() >= remoteStageSet.basedOnVersion.v()) localStageSet.basedOnVersion.v() else remoteStageSet.basedOnVersion.v()
        mergeStageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_MERGED, remoteStageSet.originCertId.v(), remoteStageSet.originServiceUuid.v(), remoteStageSet.version.v(), basedOnVersion)
        obsoleteStageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_SERVER, FileSyncStrings.STAGESET_STATUS_DELETE, remoteStageSet.originCertId.v(), remoteStageSet.originServiceUuid.v(), remoteStageSet.version.v(), basedOnVersion)

    }

    /**
     * merges already merged [StageSet] with itself and predecessors (Fs->Left->Right) to find parent directories
     * which were not in the right StageSet but in the left. When a directory has changed its content
     * on the left side but not/or otherwise changed on the right
     * it is missed there and has a wrong content hash.
     *
     * @throws SqlQueriesException
     */
    @Throws(SqlQueriesException::class)
    fun directoryStuff() {
        val oldeMergedSetId = mergeStageSet.id.v()
        val oldeIdNewIdMapForDirectories: MutableMap<Long, Long> = HashMap()
        order = Order()
        val targetStageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_MERGED, mergeStageSet.originCertId.v(), mergeStageSet.originServiceUuid.v(), mergeStageSet.version.v(), basedOnVersion)
        N.sqlResource(stageDao.getStagesResource(oldeMergedSetId)) { stageSet: ISQLResource<Stage> ->
            var rightStage = stageSet.next
            while (rightStage != null) {
                if (rightStage.isDirectory) {
                    var contentHashDummy: FsDirectory = fsDao.getDirectoryById(rightStage.fsId)
                    var content: List<Stage>? = null
                    if (contentHashDummy == null) { // it is not in fs. just add every child from the Stage
                        contentHashDummy = FsDirectory()
                        content = stageDao.getStageContent(rightStage.id)
                        for (stage in content) {
                            if (!stage.deleted) {
                                if (stage.isDirectory) contentHashDummy.addDummySubFsDirectory(stage.name) else contentHashDummy.addDummyFsFile(stage.name)
                            }
                            stageDao.flagMerged(stage.id, true)
                        }
                    } else { // fill with info from FS
                        val fsContent: List<GenericFSEntry> = fsDao.getContentByFsDirectory(contentHashDummy.id.v())
                        contentHashDummy.addContent(fsContent)
                        mergeFsDirectoryWithSubStages(contentHashDummy, rightStage)
                    }
                    // apply delta
                    contentHashDummy.calcContentHash()
                    rightStage.contentHash = contentHashDummy.contentHash.v()
                }
                saveRightStage(rightStage, targetStageSet.id.v(), oldeIdNewIdMapForDirectories)
                rightStage = stageSet.next
            }
        }
        stageDao.deleteStageSet(oldeMergedSetId)
        stageDao.flagMergedStageSet(targetStageSet.id.v(), false)
        mergeStageSet = targetStageSet
    }

    @Throws(SqlQueriesException::class)
    private fun saveRightStage(stage: Stage, stageSetId: Long, oldeIdNewIdMapForDirectories: MutableMap<Long, Long>) {
        val oldeId = stage.id
        val parentId = stage.parentId
        if (parentId != null && oldeIdNewIdMapForDirectories.containsKey(parentId)) {
            stage.parentId = oldeIdNewIdMapForDirectories[parentId]
        }
        stage.id = null
        stage.stageSet = stageSetId
        stage.order = order.ord()
        stageDao.insert(stage)
        if (stage.isDirectory) oldeIdNewIdMapForDirectories[oldeId] = stage.id
    }

    @Throws(SqlQueriesException::class)
    private fun mergeFsDirectoryWithSubStages(fsDirToMergeInto: FsDirectory, stageToMergeWith: Stage) {
        val content = stageDao.getStageContent(stageToMergeWith.id)
        for (stage in content) {
            if (stage.isDirectory) {
                if (stage.deleted) {
                    fsDirToMergeInto.removeSubFsDirecoryByName(stage.name)
                } else {
                    fsDirToMergeInto.addDummySubFsDirectory(stage.name)
                }
            } else {
                if (stage.deleted) {
                    fsDirToMergeInto.removeFsFileByName(stage.name)
                } else {
                    fsDirToMergeInto.addDummyFsFile(stage.name)
                }
            }
            stageDao.flagMerged(stage.id, true)
        }
    }

    @Throws(SqlQueriesException::class)
    fun cleanup() { //cleanup
        stageDao.deleteStageSet(remoteStageSet.id.v())
        mergeStageSet.setStatus(FileSyncStrings.STAGESET_STATUS_STAGED)
        stageDao.updateStageSet(mergeStageSet)
        stageDao.deleteStageSet(obsoleteStageSet.getId().v())
        if (!stageDao.stageSetHasContent(mergeStageSet.id.v())) stageDao.deleteStageSet(mergeStageSet.id.v())
    }

    fun isSolved() = conflictMap.all { it.value.hasChoice }

    private var solving: Boolean = false

    fun isSolving() = solving

    fun setSolving(v: Boolean) {
        this.solving = v
    }


    fun findConflicts(): ConflictSolver {
        /**
         * c1 conflicts have no parents since they only consist of stages that differ in contentHash and are not deleted.
         * c2 covers conflicts that have a parent-child-relation:
         *      eg. local changes file /foo/bar.txt and remote deletes /foo/
         *      this Conflict has no localStage but a parent Conflict
         *      c3 is c2 "inverted" or with the StageSets swapped.
         */
        val c1 = conflictDao.getC1Conflicts(localStageSetId = localStageSet.id.v(), remoteStageSetId = remoteStageSet.id.v())
        val c2 = conflictDao.getLocalDeletedByRemote(localStageSetId = localStageSet.id.v(), remoteStageSetId = remoteStageSet.id.v())
        val c3 = conflictDao.getRemoteDeletedByLocal(localStageSetId = localStageSet.id.v(), remoteStageSetId = remoteStageSet.id.v())
        createConflicts(c1)
        createConflicts(c2)
        createConflicts(c3)
        /**
         * find root conflicts here. they have no parent conflicts
         */
        return this
    }

    private fun createConflicts(dbConflicts: List<DbConflict>) {
        dbConflicts.forEach loop@{ dbConflict ->
            val localStage = if (dbConflict.localStageId.notNull()) stageDao.getStageById(dbConflict.localStageId.v()) else null
            val remoteStage = if (dbConflict.remoteStageId.notNull()) stageDao.getStageById(dbConflict.remoteStageId.v()) else null
            val conflict = Conflict(conflictDao, localStage, remoteStage)
            if (conflictMap.containsKey(conflict.key))
                return@loop
            conflictMap[conflict.key] = conflict
            rootConflictMap[conflict.key] = conflict
            localStage?.let { l ->
                localStageConflictMap[l.id] = conflict
                // Find parent conflicts
                if (l.parentIdPair.notNull() && localStageConflictMap.containsKey(l.parentId)) {
                    val parent = localStageConflictMap[l.parentId]!!
                    parent.addChild(conflict)
                    rootConflictMap.remove(conflict.key)
                }
            }
            remoteStage?.let { r ->
                remoteStageConflictMap[r.id] = conflict
                // Find parent conflicts
                if (r.parentIdPair.notNull() && remoteStageConflictMap.containsKey(r.parentId)) {
                    val parent = remoteStageConflictMap[r.parentId]!!
                    parent.addChild(conflict)
                    rootConflictMap.remove(conflict.key)
                }
            }
        }
    }

    fun hasConflicts(): Boolean = conflictMap.isNotEmpty()

    companion object {
        @JvmStatic
        fun createIdentifier(localStageSetId: Long, rightStageSetId: Long): String = "$localStageSetId:$rightStageSetId"
    }
}