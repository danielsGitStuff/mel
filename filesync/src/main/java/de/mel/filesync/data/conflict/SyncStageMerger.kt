package de.mel.filesync.data.conflict

import de.mel.filesync.sql.FsDirectory
import de.mel.filesync.sql.Stage
import de.mel.filesync.sql.StageSet
import de.mel.filesync.sql.dao.ConflictDao
import de.mel.sql.SqlQueriesException
import java.util.*

/**
 * Created by xor on 5/6/17.
 */
abstract class SyncStageMerger(protected val conflictDao: ConflictDao, protected val lStageSetId: Long, protected val rStageSetId: Long) {
//    protected val idMapRemote: Map<Long, Long> = HashMap()
//    protected val idMapLocal: Map<Long, Long> = HashMap()
    protected val stageDao = conflictDao.stageDao
    protected val fsDao = conflictDao.fsDao

    lateinit var mergedStageSet: StageSet

    /**
     * is called with two Stages which reference the same logical File or Directory
     *
     * @param left
     * @param right
     * @throws SqlQueriesException
     */
    @Throws(SqlQueriesException::class)
    abstract fun foundLocal(local: Stage, remote: Stage?)

    @Throws(SqlQueriesException::class)
    abstract fun foundRemote(remote: Stage)

    fun calcDirectoryContentHashes() {
        stageDao.getDirectoriesByStageSet(mergedStageSet.id.v()).forEach { directory ->
            val dirDummy = FsDirectory()
            val content = stageDao.getNotDeletedContent(directory.id).map { stageDao.stage2FsEntry(it).toGeneric() }
            dirDummy.addContent(content)
            dirDummy.calcContentHash()
            stageDao.updateContentHash(directory.id, dirDummy.contentHash.v())
        }
    }

}