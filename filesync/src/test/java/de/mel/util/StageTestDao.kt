package de.mel.util

import de.mel.TestDao
import de.mel.filesync.sql.Stage
import de.mel.sql.ISQLQueries

class StageTestDao(sqlQueries: ISQLQueries) : TestDao<Stage>(sqlQueries) {
    override fun createName(obj: Stage): String = obj.name
}