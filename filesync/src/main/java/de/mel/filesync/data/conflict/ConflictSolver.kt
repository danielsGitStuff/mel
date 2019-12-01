package de.mel.filesync.data.conflict

import de.mel.filesync.sql.FileSyncDatabaseManager
import de.mel.filesync.sql.StageSet

/**
 * Finds Conflicts according to @see bla
 * todo add link do conflict image
 */
class ConflictSolver(val fileSyncDatabaseManager: FileSyncDatabaseManager, val localStageSet: StageSet, val remoteStageSet: StageSet) {
    val conflictDao = fileSyncDatabaseManager.conflictDao

    fun findConflicts() {
        val c1 = conflictDao.getC1Conflicts(localStageSetId = localStageSet.id.v(), remoteStageSetId = remoteStageSet.id.v())
        val c2 = conflictDao.getLocalDeletedByRemote(localStageSetId = localStageSet.id.v(), remoteStageSetId = remoteStageSet.id.v())
        val c3 = conflictDao.getRemoteDeletedByLocal(localStageSetId = localStageSet.id.v(), remoteStageSetId = remoteStageSet.id.v())
    }

    companion object {
        fun createIdentifier(localStageSetId: Long, rightStageSetId: Long): String = "$localStageSetId:$rightStageSetId"
    }
}