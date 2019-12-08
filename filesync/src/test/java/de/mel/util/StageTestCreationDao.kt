package de.mel.util

import de.mel.filesync.sql.Stage
import de.mel.sql.ISQLQueries
import de.mel.sql.test.TestCreationDao
import java.io.File

class StageTestCreationDao(dbFile: File) : TestCreationDao<Stage>(dbFile) {
    override fun createName(obj: Stage): String = obj.name

}