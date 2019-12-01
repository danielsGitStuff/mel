package de.mel.filesync.sql.dao

import de.mel.filesync.data.conflict.DbConflict
import de.mel.filesync.sql.Stage
import de.mel.sql.Dao
import de.mel.sql.ISQLQueries
import de.mel.sql.ISQLResource
import de.mel.sql.SQLQueries

/**
 * Contains methods finding Conflicts.
 * //todo link to white board image
 */
class ConflictDao(val stageDao: StageDao) : Dao(stageDao.sqlQueries) {
    val s = Stage()
    val dbc = DbConflict()
    /**
     *
     */
    fun getC1Conflicts(localStageSetId: Long, remoteStageSetId: Long): ISQLResource<DbConflict>? {
        //SELECT s.name, s.id,s.stageset, ss.id,ss.stageset from (select name,id,contenthash,path, stageset from stage where stageset=1) s
        // left join (select name,id,contenthash,stageset,path from stage where stageset=3) ss on (s.name=ss.name and s.path=ss.path) where s.contenthash<>ss.contenthash
        val query = """
            select l.${s.idPair.k()} as ${dbc.localStageId.k()}, r.${s.idPair.k()} as ${dbc.remoteStageId.k()} from (select ${s.idPair.k()},${s.namePair.k()},${s.deletedPair.k()},${s.contentHashPair.k()},${s.pathPair.k()} from stage where stageset=? and ${s.deletedPair.k()}=0) l
            left join (select id,${s.namePair.k()},${s.deletedPair.k()},${s.contentHashPair.k()},${s.pathPair.k()} from stage where stageset=? and ${s.deletedPair.k()}=0) r
            on (l.${s.pathPair.k()}=r.${s.pathPair.k()} and l.${s.namePair.k()}=r.${s.namePair.k()}) where l.${s.contentHashPair.k()}<>r.${s.contentHashPair.k()}
            """

        return sqlQueries.loadQueryResource(query, dbc.allAttributes, DbConflict::class.java, ISQLQueries.args(localStageSetId,false,remoteStageSetId,false))
    }

    fun getLocalDeletedByRemote(localStageSetId: Long, remoteStageSetId: Long) {
//        select l.name,l.id,l.stageset,l.contenthash,r.contenthash,l.deleted,r.deleted,l.path||l.name,r.path||r.name, r.id
//        from (select name,id,stageset,contenthash,path,deleted from stage where stageset=1) l
//                join (select name,id,stageset,contenthash,path,deleted from stage where stageset=3) r
//                on (l.path like r.path||r.name||"/%") where r.deleted = 1 and l.deleted=0

    }

    fun getRemoteDeletedByLocal(localStageSetId: Long, remoteStageSetId: Long) {
        getC1Conflicts(remoteStageSetId, localStageSetId)
    }
}