package de.mel.util

import de.mel.filesync.sql.Stage
import de.mel.sql.ISQLQueries
import de.mel.sql.SQLQueries
import de.mel.sql.conn.SQLConnection
import de.mel.sql.test.TestCreationDao
import java.io.File

class StageTestCreationDao : TestCreationDao<Stage> {
    constructor(creationLocalDao: StageTestCreationDao) : super(creationLocalDao)

    constructor(dbFile: File) : super(dbFile)

    constructor(sqlQueries: ISQLQueries) : super(sqlQueries)

    override fun createName(obj: Stage): String = obj.name
    override fun afterInsert(obj: Stage, id: Long) {
        obj.id = id
    }

    /**
     * deletes from cache and db
     */
    fun delete(name: String): StageTestCreationDao {
        val stage = nameMap.remove(name)!!
        sqlQueries.delete(stage, "${stage.idPair.k()}=?", ISQLQueries.args(stage.id))
        return this
    }

    fun reloadStageSet(stageSetId: Long): StageTestCreationDao {
        val dummy = Stage()
        val where = "${dummy.stageSetPair.k()}=?"
        sqlQueries.load(dummy.allAttributes, dummy, where, ISQLQueries.args(stageSetId)).forEach {
            nameMap[it.name] = it
        }
        return this
    }

}