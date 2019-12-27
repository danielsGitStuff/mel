package de.mel.filesync.data.conflict

import de.mel.auth.tools.N
import de.mel.core.serialize.serialize.tools.OTimer
import de.mel.filesync.sql.FsDirectory
import de.mel.filesync.sql.Stage
import de.mel.filesync.sql.StageSet
import de.mel.filesync.sql.dao.ConflictDao
import de.mel.sql.ISQLResource
import de.mel.sql.SqlQueriesException

/**
 * Created by xor on 5/6/17.
 */
abstract class SyncStageMerger(protected val conflictDao: ConflictDao, val localStageSet: StageSet, val remoteStageSet: StageSet) {
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

//    fun calcDirectoryContentHashes() {
//        stageDao.getDirectoriesByStageSet(mergedStageSet.id.v()).forEach { directory ->
//            val dirDummy = FsDirectory()
//            val content = stageDao.getNotDeletedContent(directory.id).map { stageDao.stage2FsEntry(it).toGeneric() }
//            dirDummy.addContent(content)
//            dirDummy.calcContentHash()
//            stageDao.updateContentHash(directory.id, dirDummy.contentHash.v())
//        }
//    }

    protected open fun before() {

    }

    protected open fun after() {

    }

    fun merge() {
        before()
        iterateStageSets()
        after()
    }

    /**
     * iterates over the left [StageSet] first and finds
     * relating entries in the right [StageSet] and flags everything it finds (in the right StageSet).
     * Afterwards it iterates over all non flagged [Stage]s of the right [StageSet].
     * Iteration is in order of the Stages insertion.
     *
     * @param localStageSet
     * @param remoteStageSet
     * @param merger
     * @throws SqlQueriesException
     */
    @Throws(SqlQueriesException::class)
    open fun iterateStageSets() {
        val timer1 = OTimer("iter 1")
        val timer2 = OTimer("iter 2")
        val timer3 = OTimer("iter 3")
        N.sqlResource(stageDao.getNotMergedStagesResource(localStageSet.id.v())) { localStages: ISQLResource<Stage?> ->
            var localStage = localStages.next
            while (localStage != null) {
                val remoteStage = stageDao.getStageByPathAndName(remoteStageSet.id.v(), localStage.path, localStage.name)
                this.foundLocal(localStage, remoteStage)
                if (remoteStage != null) stageDao.flagMerged(remoteStage.id, true)
                localStage = localStages.next
            }
        }
        N.sqlResource(stageDao.getNotMergedStagesResource(remoteStageSet.id.v())) { remoteStages: ISQLResource<Stage?> ->
            var remoteStage = remoteStages.next
            while (remoteStage != null) {
                this.foundRemote(remoteStage)
                remoteStage = remoteStages.next
            }
        }
        timer1.print().reset()
        timer2.print().reset()
        timer3.print().reset()
    }
}