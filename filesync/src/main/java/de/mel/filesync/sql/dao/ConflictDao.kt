package de.mel.filesync.sql.dao

import de.mel.filesync.sql.Stage
import de.mel.sql.Dao
import de.mel.sql.SQLQueries

/**
 * Contains methods finding Conflicts.
 * //todo link to white board image
 */
class ConflictDao(val stageDao: StageDao) : Dao(stageDao.sqlQueries) {
    val s = Stage()
    /**
     *
     */
    fun getC1Conflicts(localStageSetId: Long, remoteStageSetId: Long) {
        //SELECT s.name, s.id,s.stageset, ss.id,ss.stageset from (select name,id,contenthash,path, stageset from stage where stageset=1) s
        // left join (select name,id,contenthash,stageset,path from stage where stageset=3) ss on (s.name=ss.name and s.path=ss.path) where s.contenthash<>ss.contenthash
    }

    fun getC2Conflicts(localStageSetId: Long, remoteStageSetId: Long) {
//        select l.name,l.id,l.stageset,l.contenthash,r.contenthash,l.deleted,r.deleted,l.path||l.name,r.path||r.name, r.id
//        from (select name,id,stageset,contenthash,path,deleted from stage where stageset=1) l
//                join (select name,id,stageset,contenthash,path,deleted from stage where stageset=3) r
//                on (l.path like r.path||r.name||"/%") where r.deleted = 1 and l.deleted=0

    }

    fun getC3Conflicts(localStageSetId: Long, remoteStageSetId: Long) {
        getC1Conflicts(remoteStageSetId, localStageSetId)
    }
}