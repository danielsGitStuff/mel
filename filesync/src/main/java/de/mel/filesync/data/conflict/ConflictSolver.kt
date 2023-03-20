package de.mel.filesync.data.conflict

import de.mel.Lok
import de.mel.auth.tools.Order
import de.mel.filesync.data.FileSyncStrings
import de.mel.filesync.sql.FsDirectory
import de.mel.filesync.sql.Stage
import de.mel.filesync.sql.StageSet
import de.mel.filesync.sql.dao.ConflictDao

/**
 * Finds Conflicts according to @see bla
 * todo add link do conflict image
 */
/**
 * Basic object that finds pairs of local and remote conflicts.
 *
 * Conflicts are based on [Stage]s of a local and a remote StageSet.
 * So it may happen that only one side has a [Stage] because it does not exist in the other.
 * Conflicts can depend on another, because they are a tree structure.
 *
 * Root dependencies, conflicts without parents, are hold in [rootConflictMap].
 *
 * Keep in mind that this does not necessarily represent the actual dependency, since it is limited to [Stage]s only.
 *
 * A conflict may have multiple child conflicts on different layers that do not occur in any StageSet and therefore
 * are represented as multiple root conflicts here.
 */
open class ConflictSolver(conflictDao: ConflictDao, localStageSet: StageSet, remoteStageSet: StageSet) :
    StageSetMerger(conflictDao, localStageSet, remoteStageSet) {
    var conflictHelperUuid: String? = null
    val basedOnVersion: Long
    val conflictMap = mutableMapOf<String, Conflict>()
    val rootConflictMap = mutableMapOf<String, Conflict>()

    val localStageConflictMap = mutableMapOf<Long, Conflict>()

    val remoteStageConflictMap = mutableMapOf<Long, Conflict>()

    private val rootConflicts = mutableListOf<Conflict>()
    var order = Order()

    val idMapLocal = mutableMapOf<Long, Long>()
    val idMapRemote = mutableMapOf<Long, Long>()

    val conflictIdentifier: String

    init {
        basedOnVersion =
            if (localStageSet.basedOnVersion.v() >= remoteStageSet.basedOnVersion.v()) localStageSet.basedOnVersion.v() else remoteStageSet.basedOnVersion.v()
        conflictIdentifier = "l${localStageSet.id.v()}//r${remoteStageSet.id.v()}"
    }

    override fun before() {
        mergedStageSet = stageDao.createStageSet(
            FileSyncStrings.STAGESET_SOURCE_MERGED,
            remoteStageSet.originCertId.v(),
            remoteStageSet.originServiceUuid.v(),
            remoteStageSet.version.v() + 1,
            remoteStageSet.version.v()
        )
    }

    override fun after() {
        // calculate content hashes of all directories
        stageDao.getNotDeletedDirectoriesByStageSet(mergedStageSet.id.v()).forEach { directory ->
            val dirDummy = FsDirectory()
            val content = stageDao.getNotDeletedContent(directory.id).map { stageDao.stage2FsEntry(it).toGeneric() }
            dirDummy.addContent(content)
            val fsContent = fsDao.getContentByFsDirectory(directory.fsId)
            dirDummy.addContent(fsContent)
            dirDummy.calcContentHash()
            stageDao.updateContentHash(directory.id, dirDummy.contentHash.v())
        }
        // reset merged flag
        stageDao.flagMergedStageSet(mergedStageSet.id.v(), false)
        // make it from fs again.
        mergedStageSet.setSource(FileSyncStrings.STAGESET_SOURCE_FS);
        mergedStageSet.setStatus(FileSyncStrings.STAGESET_STATUS_STAGED)
        stageDao.updateStageSet(mergedStageSet)
        // empty stage sets must be removed
        if (!stageDao.stageSetHasContent(mergedStageSet.id.v())) {
            stageDao.deleteStageSet(mergedStageSet.id.v())
        }

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
        val c1 =
            conflictDao.getC1Conflicts(localStageSetId = localStageSet.id.v(), remoteStageSetId = remoteStageSet.id.v())
        val c2 = conflictDao.getLocalDeletedByRemote(
            localStageSetId = localStageSet.id.v(),
            remoteStageSetId = remoteStageSet.id.v()
        )
        val c3 = conflictDao.getRemoteDeletedByLocal(
            localStageSetId = localStageSet.id.v(),
            remoteStageSetId = remoteStageSet.id.v()
        )
        createConflicts(c1)
        createConflicts(c2)
        createConflicts(c3)
        /**
         * find root conflicts here. they have no parent conflicts
         */
        return this
    }

    fun rec(cc: Conflict, localStage: Stage?, remoteStage: Stage?) {
        val remoteContentList: List<Stage> = if (remoteStage == null) emptyList() else stageDao.getStageContent(remoteStage.id)
        val localContentList: List<Stage> = if (localStage == null) emptyList() else stageDao.getStageContent(localStage.id)
        val remoteContent: Map<String, Stage> = remoteContentList.map { it.name to it }.toMap()
        val localContent: Map<String, Stage> = localContentList.map { it.name to it }.toMap()
        find(cc, localContent, remoteContent)
        find(cc, remoteContent, localContent, c1IsLocal = false)
    }

    fun find(con: Conflict, c1: Map<String, Stage>, c2: Map<String, Stage>, c1IsLocal: Boolean = true) {
        for ((name, c1Stage) in c1) {
            if (c2.containsKey(name)) {
                val c2Stage = c2[name]!!
                if (c2Stage.contentHash != c1Stage.contentHash) {
                    var c = if (c1IsLocal)
                        Conflict(conflictDao, c1Stage, c2Stage)
                    else
                        Conflict(conflictDao, c2Stage, c1Stage)
                    c = con.addChild(c)
                    if (c1IsLocal) {
                        localStageConflictMap[c1Stage.id] = c
                        remoteStageConflictMap[c2Stage.id] = c
                        rec(c, c1Stage, c2Stage)
                    } else {
                        localStageConflictMap[c2Stage.id] = c
                        remoteStageConflictMap[c1Stage.id] = c
                        rec(c, c2Stage, c1Stage)
                    }
                }
            } else {
                var c = if (c1IsLocal) Conflict(conflictDao, c1Stage, null) else Conflict(conflictDao, null, c1Stage)
                c = con.addChild(c)
                if (c1IsLocal) {
                    localStageConflictMap[c1Stage.id] = c
                    rec(c, c1Stage, null)
                } else {
                    remoteStageConflictMap[c1Stage.id] = c
                    rec(c, null, c1Stage)
                }
            }
        }
    }

    private fun createConflicts(dbConflicts: List<DbConflict>) {
        dbConflicts.forEach loop@{ dbConflict ->
            val localStage =
                if (dbConflict.localStageId.notNull()) stageDao.getStageById(dbConflict.localStageId.v()) else null
            val remoteStage =
                if (dbConflict.remoteStageId.notNull()) stageDao.getStageById(dbConflict.remoteStageId.v()) else null
            var conflict = Conflict(conflictDao, localStage, remoteStage)
            if (conflictMap.containsKey(conflict.key))
                return@loop
            conflictMap[conflict.key] = conflict
            rootConflictMap[conflict.key] = conflict
            if (conflict.key == "7/17")
                Lok.debug("debug tes 1")
            localStage?.let { l ->
                // Find parent conflicts
                if (l.parentIdPair.notNull() && localStageConflictMap.containsKey(l.parentId)) {
                    val parent = localStageConflictMap[l.parentId]!!
                    conflict = parent.addChild(conflict)
                    rootConflictMap.remove(conflict.key)
                }
                localStageConflictMap[l.id] = conflict
            }
            remoteStage?.let { r ->
                remoteStageConflictMap[r.id] = conflict
                // Find parent conflicts
                if (r.parentIdPair.notNull() && remoteStageConflictMap.containsKey(r.parentId)) {
                    val parent = remoteStageConflictMap[r.parentId]!!
                    conflict = parent.addChild(conflict)
                    rootConflictMap.remove(conflict.key)
                }
            }
            if (remoteStage != null && localStage != null) {
                Lok.debug("debug WIP content missing")
                if (remoteStage.isDirectory || localStage.isDirectory) {

                    rec(conflict, localStage, remoteStage)
                    Lok.debug("debug o3kaf")
                }
            }
        }
    }

    fun hasConflicts(): Boolean = !conflictMap.all { it.value.hasChoice }


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
            if (rejectedStage.depth == decisionStage.depth) {
                decisionStage.fsId = rejectedStage.fsId
                decisionStage.fsParentId = rejectedStage.fsParentId
            }
            // get the fs parent id as well
            // todo performance, do something that does not query the database
            if (decisionStage.fsParentId == null) {
                stageDao.getStageById(decisionStage.parentId)?.let {
                    decisionStage.fsParentId = it.fsId
                }
            }
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
            if (conflict.decision!!.depth >= conflict.rejection!!.depth) {
                insertToMerged(conflict.decision!!, conflict.rejection)
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
                    insertToMerged(conflict.decision!!, conflict.rejection)
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
            if (conflict == null || conflict.decision == null || conflict.rejection == null)
                Lok.debug("debug fix null here")
            if (conflict.decision == conflict.remoteStage && conflict.remoteStage != null) {
                insertToMerged(conflict.decision!!, conflict.rejection)
            } else if (conflict.decision != null && conflict.rejection != null && conflict.decision!!.deleted && conflict.decision!!.depth <= conflict.rejection!!.depth)
                return
            else if (conflict.remoteStage?.deleted != true && conflict.chosenLocal) {
                // stage exists in remote but not locally. it must be flagged
                val r: Stage = conflict.remoteStage!!
                val m = Stage(this.mergedStageSet.id.v(), r)
                    .setDeleted(true)
                    .setOrder(0L)
                    .setVersion(mergedStageSet.version.v())
                    .setParentId(idMapRemote[r.parentId])
                conflictDao.stageDao.insert(m);
                Lok.debug()
            }
//            if (conflict.decision?.depth == conflict.rejection?.depth && conflict.decision!=null){
//
//            }
//            if (conflict.decision!!.depth == conflict.rejection!!.depth) {
//                insertToMerged(conflict.decision!!, null)
//            } else if (conflict.decision!!.deleted && conflict.decision!!.depth <= conflict.rejection!!.depth)
//                return
            return
        }
    }


    companion object {
        @JvmStatic
        fun createIdentifier(localStageSetId: Long, rightStageSetId: Long): String = "$localStageSetId:$rightStageSetId"
    }
}