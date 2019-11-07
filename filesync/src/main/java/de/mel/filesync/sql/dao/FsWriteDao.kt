package de.mel.filesync.sql.dao

import de.mel.Lok
import de.mel.filesync.sql.FileSyncDatabaseManager
import de.mel.filesync.sql.FsFile
import de.mel.filesync.sql.FsWriteFile
import de.mel.sql.ISQLQueries

class FsWriteDao(val fileSyncDatabaseManager: FileSyncDatabaseManager, val isqlQueries: ISQLQueries) : FsDao(fileSyncDatabaseManager, isqlQueries) {
    init {
        dummy = FsWriteFile()
    }
}