package de.mel.filesync.data.conflict

import de.mel.filesync.sql.FileSyncDatabaseManager
import de.mel.filesync.sql.StageSet

/**
 * Finds Conflicts according to @see bla
 * todo add link do conflict image
 */
class ConflictSolver(val fileSyncDatabaseManager: FileSyncDatabaseManager, val localStageSet: StageSet, val remoteStageSet: StageSet) {
    val conflictDao = fileSyncDatabaseManager.conflictDao
    val stageDao = fileSyncDatabaseManager.stageDao
    private val conflictMap = mutableMapOf<String, Conflict>()

    fun findConflicts() {
        val c1 = conflictDao.getC1Conflicts(localStageSetId = localStageSet.id.v(), remoteStageSetId = remoteStageSet.id.v())
        val c2 = conflictDao.getLocalDeletedByRemote(localStageSetId = localStageSet.id.v(), remoteStageSetId = remoteStageSet.id.v())
        val c3 = conflictDao.getRemoteDeletedByLocal(localStageSetId = localStageSet.id.v(), remoteStageSetId = remoteStageSet.id.v())
        createConflicts(c1)
        createConflicts(c2)
        createConflicts(c3)
    }

    private fun createConflicts(dbConflicts: List<DbConflict>) {
        dbConflicts.forEach {
            val localStage = if (it.localStageId.notNull()) stageDao.getStageById(it.localStageId.v()) else null
            val remoteStage = if (it.remoteStageId.notNull()) stageDao.getStageById(it.remoteStageId.v()) else null
            val conflict = Conflict(conflictDao, localStage, remoteStage)

        }
    }


    companion object {
        @JvmStatic
        fun createIdentifier(localStageSetId: Long, rightStageSetId: Long): String = "$localStageSetId:$rightStageSetId"
    }
}