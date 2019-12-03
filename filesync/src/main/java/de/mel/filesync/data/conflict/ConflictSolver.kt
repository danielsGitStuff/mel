package de.mel.filesync.data.conflict

import de.mel.filesync.sql.FileSyncDatabaseManager
import de.mel.filesync.sql.StageSet
import de.mel.filesync.sql.dao.ConflictDao

/**
 * Finds Conflicts according to @see bla
 * todo add link do conflict image
 */
class ConflictSolver(private val conflictDao: ConflictDao, val localStageSet: StageSet, val remoteStageSet: StageSet) {
    val stageDao = conflictDao.stageDao
    private val conflictMap = mutableMapOf<String, Conflict>()
    private val localStageConflictMap = mutableMapOf<Long, Conflict>()
    private val remoteStageConflictMap = mutableMapOf<Long, Conflict>()
    private val rootConflicts = mutableListOf<Conflict>()


    fun findConflicts() {
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
    }

    private fun createConflicts(dbConflicts: List<DbConflict>) {
        dbConflicts.forEach loop@{ dbConflict ->
            val localStage = if (dbConflict.localStageId.notNull()) stageDao.getStageById(dbConflict.localStageId.v()) else null
            val remoteStage = if (dbConflict.remoteStageId.notNull()) stageDao.getStageById(dbConflict.remoteStageId.v()) else null
            val conflict = Conflict(conflictDao, localStage, remoteStage)
            if (conflictMap.containsKey(conflict.key))
                return@loop
            conflictMap[conflict.key] = conflict
            localStage?.let { l ->
                localStageConflictMap[l.id] = conflict
                // Find parent conflicts
                if (l.parentIdPair.notNull() && localStageConflictMap.containsKey(l.parentId)) {
                    val parent = localStageConflictMap[l.parentId]!!
                    parent.assignChild(conflict)
                }
            }
            remoteStage?.let { r ->
                remoteStageConflictMap[r.id] = conflict
                // Find parent conflicts
                if (r.parentIdPair.notNull() && remoteStageConflictMap.containsKey(r.parentId)) {
                    val parent = remoteStageConflictMap[r.parentId]!!
                    parent.assignChild(conflict)
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun createIdentifier(localStageSetId: Long, rightStageSetId: Long): String = "$localStageSetId:$rightStageSetId"
    }
}