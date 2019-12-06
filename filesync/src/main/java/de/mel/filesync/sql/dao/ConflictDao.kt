package de.mel.filesync.sql.dao

import de.mel.filesync.data.conflict.DbConflict
import de.mel.filesync.sql.Stage
import de.mel.sql.Dao
import de.mel.sql.ISQLQueries
import java.io.File

/**
 * Contains methods finding Conflicts.
 * //todo link to white board image
 */
class ConflictDao(val stageDao: StageDao, val fsDao: FsDao) : Dao(stageDao.sqlQueries) {
    val s = Stage()
    val dbc = DbConflict()
    /**
     *
     */
    fun getC1Conflicts(localStageSetId: Long, remoteStageSetId: Long): List<DbConflict> {
        //SELECT s.name, s.id,s.stageset, ss.id,ss.stageset from (select name,id,contenthash,path, stageset from stage where stageset=1) s
        // left join (select name,id,contenthash,stageset,path from stage where stageset=3) ss on (s.name=ss.name and s.path=ss.path) where s.contenthash<>ss.contenthash
        val query = """
            select l.${s.idPair.k()} as ${dbc.localStageId.k()}, r.${s.idPair.k()} as ${dbc.remoteStageId.k()} from (select ${s.idPair.k()},${s.namePair.k()},${s.deletedPair.k()},${s.contentHashPair.k()},${s.pathPair.k()} from stage where stageset=? and ${s.deletedPair.k()}=0) l
            left join (select id,${s.namePair.k()},${s.deletedPair.k()},${s.contentHashPair.k()},${s.pathPair.k()} from stage where stageset=? and ${s.deletedPair.k()}=0) r
            on (l.${s.pathPair.k()}=r.${s.pathPair.k()} and l.${s.namePair.k()}=r.${s.namePair.k()}) where l.${s.contentHashPair.k()}<>r.${s.contentHashPair.k()}
            """
        return sqlQueries.loadQueryResource(query, dbc.allAttributes, DbConflict::class.java, ISQLQueries.args(localStageSetId, false, remoteStageSetId, false)).toList();
    }

    fun getLocalDeletedByRemote(localStageSetId: Long, remoteStageSetId: Long): List<DbConflict> {
//        select l.name,l.id,l.stageset,l.contenthash,r.contenthash,l.path||l.name,r.path||r.name, r.id
//        from (select name,id,stageset,contenthash,path,deleted from stage where stageset=1) l
//                join (select name,id,stageset,contenthash,path,deleted from stage where stageset=3) r
//                on (l.path like r.path||r.name||"/%" or (l.name=r.name and l.path=r.path)) where r.deleted = 1 and l.deleted=0
        val query = """
            select l.${s.namePair.k()} as ${dbc.localStageId.k()}, r.${s.idPair.k()} as ${dbc.remoteStageId.k()},
            r.${s.orderPair.k()} as lengthr
            from (select ${s.namePair.k()}, ${s.pathPair.k()}, ${s.deletedPair.k()} from ${s.tableName} where ${s.stageSetPair.k()}=?) l
            join (select ${s.namePair.k()}, ${s.pathPair.k()}, ${s.deletedPair.k()} from ${s.tableName} where ${s.stageSetPair.k()}=?) r
            on (l.${s.pathPair.k()} like r.${s.pathPair.k()}||r.${s.namePair.k()}||"${File.separator}%" 
            or (l.${s.namePair.k()}=r.${s.namePair.k()} and l.${s.pathPair.k()}=r.${s.pathPair.k()}))
            where r.${s.deletedPair.k()}=? and l.${s.deletedPair.k()}=? order by lengthr
        """.trimIndent()
        return sqlQueries.loadQueryResource(query, dbc.allAttributes, DbConflict::class.java, ISQLQueries.args(localStageSetId, remoteStageSetId, true, false)).toList()
    }

    fun getRemoteDeletedByLocal(localStageSetId: Long, remoteStageSetId: Long): List<DbConflict> {
        return getC1Conflicts(remoteStageSetId, localStageSetId)
    }
}