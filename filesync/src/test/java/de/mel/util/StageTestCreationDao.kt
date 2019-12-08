package de.mel.util

import de.mel.filesync.sql.Stage
import de.mel.sql.test.TestCreationDao
import java.io.File

class StageTestCreationDao : TestCreationDao<Stage> {
    constructor(creationLocalDao: StageTestCreationDao) : super(creationLocalDao)

    constructor(dbFile: File) : super(dbFile)

    override fun createName(obj: Stage): String = obj.name
    override fun afterInsert(obj: Stage, id: Long) {
        obj.id = id
    }

}