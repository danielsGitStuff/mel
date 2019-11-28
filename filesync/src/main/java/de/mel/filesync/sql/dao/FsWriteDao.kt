package de.mel.filesync.sql.dao

import de.mel.execute.SqliteExecutor
import de.mel.filesync.sql.*
import de.mel.sql.ISQLQueries

class FsWriteDao(val fileSyncDatabaseManager: FileSyncDatabaseManager, val isqlQueries: ISQLQueries) : FsDao(fileSyncDatabaseManager, isqlQueries) {
    private val fsTable = FsFile().tableName
    private val fsWriteTable = CreationScripts().tableName
    private val fsBackupTable = "${FsFile().tableName}_bak"


    fun prepare() {
        cleanUp()
        val creationScripts = CreationScripts()
        val executor = SqliteExecutor(sqlQueries.sqlConnection)
        executor.executeStream(creationScripts.createFsWrite.byteInputStream())
        sqlQueries.execute("insert into $fsWriteTable select * from $fsTable", null)
    }

    fun cleanUp() {
        val tableName = CreationScripts().tableName
        sqlQueries.execute("drop table if exists $tableName", null)
    }

    fun commit() {
        // remove old backup table if exists
        sqlQueries.execute("drop table if exists $fsBackupTable")
        // move old fs table for backup reasons
        sqlQueries.execute("alter table $fsTable rename to $fsBackupTable")
        // move tmp table to fs
        sqlQueries.execute("alter table $fsWriteTable rename to $fsTable")
        cleanUp()
    }

    init {
        dummy = FsWriteEntry()
        tableName = dummy.tableName
    }


}