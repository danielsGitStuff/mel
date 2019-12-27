package de.mel.filesync.data.conflict

import de.mel.Lok
import de.mel.auth.tools.Order
import de.mel.filesync.data.FileSyncStrings
import de.mel.filesync.sql.FsDirectory
import de.mel.filesync.sql.Stage
import de.mel.filesync.sql.StageSet
import de.mel.filesync.sql.dao.ConflictDao
import de.mel.sql.SqlQueriesException

/**
 * Finds Conflicts according to @see bla
 * todo add link do conflict image
 */
open class ConflictSolver(conflictDao: ConflictDao, localStageSet: StageSet, remoteStageSet: StageSet) : SyncStageMerger(conflictDao, localStageSet, remoteStageSet) {
    var conflictHelperUuid: String? = null
    val basedOnVersion: Long
    val conflictMap = mutableMapOf<String, Conflict>()
    val rootConflictMap = mutableMapOf<String, Conflict>()

    val localStageConflictMap = mutableMapOf<Long, Conflict>()

    val remoteStageConflictMap = mutableMapOf<Long, Conflict>()

    private val rootConflicts = mutableListOf<Conflict>()
    private var obsoleteStageSet: StageSet
    var order = Order()

    val idMapLocal = mutableMapOf<Long, Long>()
    val idMapRemote = mutableMapOf<Long, Long>()

    init {
        basedOnVersion = if (localStageSet.basedOnVersion.v() >= remoteStageSet.basedOnVersion.v()) localStageSet.basedOnVersion.v() else remoteStageSet.basedOnVersion.v()
        mergedStageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_MERGED, remoteStageSet.originCertId.v(), remoteStageSet.originServiceUuid.v(), remoteStageSet.version.v(), basedOnVersion)
        obsoleteStageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_SERVER, FileSyncStrings.STAGESET_STATUS_DELETE, remoteStageSet.originCertId.v(), remoteStageSet.originServiceUuid.v(), remoteStageSet.version.v(), basedOnVersion)

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
        mergedStageSet.setStatus(FileSyncStrings.STAGESET_STATUS_STAGED)
        stageDao.updateStageSet(mergedStageSet)
        stageDao.deleteStageSet(obsoleteStageSet.getId().v())
        if (!stageDao.stageSetHasContent(mergedStageSet.id.v())) stageDao.deleteStageSet(mergedStageSet.id.v())
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

    fun hasConflicts(): Boolean = !conflictMap.all { it.value.hasChoice }
//    fun merge() {
//        val idMapLocal = mutableMapOf<Long, Long>()
//        val idMapRemote = mutableMapOf<Long, Long>()
//        val localStages = stageDao.getStagesByStageSet(localStageSet.id.v())
//        var localStage = localStages.next
//        fun insertToMap(map: MutableMap<Long, Long>, stage: Stage?) {
//            stage?.let {
//                stage.stageSet = mergedStageSet.id.v()
//                //todo kotlin accessor
//                if (stage.parentIdPair.notNull()) {
//                    map[stage.parentId]?.let { mergedParentId ->
//                        stage.setParentId(mergedParentId)
//                    }
//                }
//            }
//        }
//        while (localStage != null) {
//            localStageConflictMap[localStage.id]?.let { conflict ->
//                if (conflict.chosenLocal)
//                    insertToMap(idMapLocal, localStage!!)
//                else {
//                    insertToMap(idMapRemote, conflict.remoteStage)
//                }
//            }
//            localStage = localStages.next
//        }
//        val remoteStages = stageDao.getStagesByStageSet(remoteStageSet.id.v())
//        var remoteStage = remoteStages.next
//        while (remoteStage != null) {
//
//            remoteStage = remoteStages.next
//        }
//    }

    /**
     * Adds the stage to the database and creates an entry in the according idMap{Local/Remote)}.
     * The Map is determined by the StageSetId. So it has to refer to the local or remote StageSet
     *
     */
    private fun insertToMerged(decisionStage: Stage, rejectedStage: Stage?) {
        // todo debug
        if (decisionStage.namePair.equalsValue("b"))
            Lok.debug()
        val decisionStageSetId = decisionStage.stageSet
        // set parent id, but remember: parent might be from local or remote.
        decisionStage.parentId?.let { parentId ->
            // search in both maps
            (idMapLocal[parentId] ?: idMapRemote[parentId])?.let { mergedParentId ->
                decisionStage.parentId = mergedParentId
            } ?: throw ConflictException("parent id found, but parent not merged yet!")
        }
        // insert into merged StageSet
        val oldId = decisionStage.id
        decisionStage.stageSet = mergedStageSet.id.v()
        // copy new fs ids
        if (rejectedStage != null && rejectedStage.stageSet == remoteStageSet.id.v()) {
            decisionStage.fsId = rejectedStage.fsId
            decisionStage.fsParentId = rejectedStage.fsParentId
        }
        stageDao.insert(decisionStage)

        // insert stage first
        when (decisionStageSetId) {
            localStageSet.id.v() -> idMapLocal
            remoteStageSet.id.v() -> idMapRemote
            else -> throw ConflictException("stageset was neither local nor remote")
        }[oldId] = decisionStage.id

        // update the rejected id too
        rejectedStage?.let {
            when (rejectedStage.stageSet) {
                localStageSet.id.v() -> idMapLocal
                remoteStageSet.id.v() -> idMapRemote
                else -> throw ConflictException("stageset was neither local nor remote")
            }[rejectedStage.id] = decisionStage.id
        }
    }

    override fun foundLocal(local: Stage, remote: Stage?) {
        /**
         * check whether the conflict is a sub conflict of another one. In this case one stage set has no entry
         * and the created conflict key does not find the according conflict.
         * This occurs when one side deletes a directory while the other changes its contents.
         */
        localStageConflictMap[local.id]?.let { conflict ->
            // only apply the top
            if (conflict.decision!!.depth == conflict.rejection!!.depth) {
                insertToMerged(conflict.decision!!, null)
            } else if (conflict.decision!!.deleted && conflict.decision!!.depth <= conflict.rejection!!.depth)
                return
            return
        }
        /**
         * check the remote set as well
         */
        if (remote != null)
            remoteStageConflictMap[remote.id]?.let { conflict ->
                if (conflict.decision!!.depth == conflict.rejection!!.depth) {
                    insertToMerged(conflict.decision!!, null)
                } else if (conflict.decision!!.deleted && conflict.decision!!.depth <= conflict.rejection!!.depth)
                    return
                return
            }
        /**
         * If conflict exists: apply its solution. Otherwise an arbitrary Stage will do, since
         * content hashes of directories have to be calculated after merging and conflicting files are in the conflict map.
         *
         */
        val key = Conflict.createKey(local, remote)
        conflictMap[key]?.let { conflict ->
            insertToMerged(conflict.decision!!, conflict.rejection)
        } ?: insertToMerged(local, remote)
    }

    override fun foundRemote(remote: Stage) {
        remoteStageConflictMap[remote.id]?.let { conflict ->
            if (conflict.decision!!.depth == conflict.rejection!!.depth) {
                insertToMerged(conflict.decision!!, null)
            } else if (conflict.decision!!.deleted && conflict.decision!!.depth <= conflict.rejection!!.depth)
                return
            return
        }
    }

    /**
     * merges already merged [StageSet] with itself and predecessors (Fs->Left->Right) to find parent directories
     * which were not in the right StageSet but in the left. When a directory has changed its content
     * on the left side but not/or otherwise changed on the right
     * it is missed there and has a wrong content hash.
     *
     * @throws SqlQueriesException
     */
    override fun after() {
        val oldeMergedSetId = mergedStageSet.id.v()
//        val oldeIdNewIdMapForDirectories: MutableMap<Long, Long> = HashMap()
//        order = Order()
        val targetStageSet = stageDao.createStageSet(FileSyncStrings.STAGESET_SOURCE_MERGED, mergedStageSet.originCertId.v(), mergedStageSet.originServiceUuid.v(), mergedStageSet.version.v(), basedOnVersion)
//        N.escalatingSqlResource(stageDao.getStagesResource(oldeMergedSetId)) { stageSet: ISQLResource<Stage> ->
//            var rightStage = stageSet.next
//            while (rightStage != null) {
//                if (rightStage.isDirectory) {
//                    val contentHashDummy = FsDirectory()
//                    var stageContent = stageDao.getStageContent(rightStage.id).map { stageDao.stage2FsEntry(it).toGeneric() }
//                    contentHashDummy.addContent(stageContent)
//                    var fsContent = fsDao.getContentByFsDirectory(rightStage.fsId)
//                    contentHashDummy.addContent(fsContent)
//                    if (contentHashDummy == null) { // it is not in fs. just add every child from the Stage
//                        contentHashDummy = FsDirectory()
//                        stageContent = stageDao.getStageContent(rightStage.id)
//                        for (stage in stageContent) {
//                            if (!stage.deleted) {
//                                if (stage.isDirectory) contentHashDummy.addDummySubFsDirectory(stage.name) else contentHashDummy.addDummyFsFile(stage.name)
//                            }
//                            stageDao.flagMerged(stage.id, true)
//                        }
//                    } else { // fill with info from FS
//                        val fsContent: List<GenericFSEntry> = fsDao.getContentByFsDirectory(contentHashDummy.id.v())
//                        contentHashDummy.addContent(fsContent)
//                        mergeFsDirectoryWithSubStages(contentHashDummy, rightStage)
//                    }
//                    // apply delta
//                    contentHashDummy.calcContentHash()
//                    rightStage.contentHash = contentHashDummy.contentHash.v()
//                }
//                saveRightStage(rightStage, targetStageSet.id.v(), oldeIdNewIdMapForDirectories)
//                rightStage = stageSet.next
//            }
//        }

        stageDao.getNotDeletedDirectoriesByStageSet(mergedStageSet.id.v()).forEach { directory ->
            val dirDummy = FsDirectory()
            val content = stageDao.getNotDeletedContent(directory.id).map { stageDao.stage2FsEntry(it).toGeneric() }
            dirDummy.addContent(content)
            val fsContent = fsDao.getContentByFsDirectory(directory.fsId)
            dirDummy.addContent(fsContent)
            dirDummy.calcContentHash()
            stageDao.updateContentHash(directory.id, dirDummy.contentHash.v())
        }

        stageDao.deleteStageSet(oldeMergedSetId)
        stageDao.flagMergedStageSet(targetStageSet.id.v(), false)
        mergedStageSet = targetStageSet
    }

    companion object {
        @JvmStatic
        fun createIdentifier(localStageSetId: Long, rightStageSetId: Long): String = "$localStageSetId:$rightStageSetId"
    }
}