package de.mel.filesync.sql

import de.mel.Lok
import de.mel.auth.data.access.FileRelatedManager
import de.mel.core.serialize.exceptions.JsonSerializationException
import de.mel.execute.SqliteExecutor
import de.mel.filesync.data.FileSyncSettings
import de.mel.filesync.data.FileSyncStrings
import de.mel.filesync.service.MelFileSyncService
import de.mel.filesync.service.sync.SyncHandler
import de.mel.filesync.sql.dao.*
import de.mel.sql.*
import de.mel.sql.conn.SQLConnector
import de.mel.sql.transform.SqlResultTransformer
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException


/**
 * does everything file (database) related
 * Created by xor on 09.07.2016.
 */
class FileSyncDatabaseManager(serviceUuid: String, workingDirectory: File?, val fileSyncSettings: FileSyncSettings) : FileRelatedManager(workingDirectory) {
    lateinit var melFileSyncService: MelFileSyncService<*>
    val conflictDao: ConflictDao
    private val sqlQueries: ISQLQueries
    val fileDistTaskDao: FileDistTaskDao
    val fsDao: FsDao
    val fsWriteDao: FsWriteDao
    val stageDao: StageDao
    val transferDao: TransferDao
    val wasteDao: WasteDao

    init {
        sqlQueries = sqlqueriesCreator.createConnection(this, serviceUuid)
        run {
            Lok.error("synchronous PRAGMA is turned off!")
            val st: SQLStatement = sqlQueries.sqlConnection.prepareStatement("PRAGMA synchronous=OFF")
            st.execute()
        }
        run {
            val st: SQLStatement = sqlQueries.sqlConnection.prepareStatement("PRAGMA foreign_keys=ON")
            st.execute()
        }
        sqlQueries.sqlConnection.setAutoCommit(false)
//        sqlQueries.enableWAL();


        val sqliteExecutor = SqliteExecutor(sqlQueries.sqlConnection)
        if (!sqliteExecutor.checkTablesExist("fsentry", "stage", "stageset", "transfer", "waste", "filedist")) {
            //find sql file in workingdir

            val creationScripts = CreationScripts()
            sqliteExecutor.executeStream(creationScripts.createFsEntry.byteInputStream())
            sqliteExecutor.executeStream(creationScripts.createFsWrite.byteInputStream())
            sqliteExecutor.executeStream(creationScripts.createFsBackup.byteInputStream())
            sqliteExecutor.executeStream(creationScripts.createRest.byteInputStream())

            hadToInitialize = true
        }
        fileSyncSettings.rootDirectory.backup()
        fsDao = FsDao(this, sqlQueries)
        fsWriteDao = FsWriteDao(this, sqlQueries)
        stageDao = StageDao(fileSyncSettings, sqlQueries, fsDao)
        transferDao = TransferDao(sqlQueries)
        wasteDao = WasteDao(sqlQueries)
        conflictDao = ConflictDao(stageDao, fsDao)
        fileDistTaskDao = FileDistTaskDao(sqlQueries)
        fsDao.setFileSyncSettings(fileSyncSettings)
        transferDao.resetStarted()
        Lok.debug("DriveDatabaseManager.initialised")
    }

    fun shutDown() {
        sqlQueries.onShutDown()
    }

    @Throws(SqlQueriesException::class)
    fun cleanUp() {
        val stage = Stage()
        val stageSet = StageSet()
        val stmt1 = "delete from " + stage.tableName
        val stmt2 = "delete from " + stageSet.tableName
        stageDao.sqlQueries.execute(stmt1, null)
        stageDao.sqlQueries.execute(stmt2, null)
    }

    interface SQLConnectionCreator {
        @Throws(SQLException::class, ClassNotFoundException::class)
        fun createConnection(fileSyncDatabaseManager: FileSyncDatabaseManager, uuid: String?): ISQLQueries
    }

    interface FileSyncSqlInputStreamInjector {
        fun createSqlFileInputStream(): InputStream?
    }

    @Throws(IllegalAccessException::class, IOException::class, JsonSerializationException::class, SqlQueriesException::class)
    fun updateVersion() {
        val version = latestVersion
        Lok.debug("updating settings, set version from " + fileSyncSettings.lastSyncedVersion.toString() + " to " + version)
        fileSyncSettings.lastSyncedVersion = version
        fileSyncSettings.save()
    }

    @get:Throws(SqlQueriesException::class)
    val latestVersion: Long
        get() = fsDao.latestVersion

    @Throws(SqlQueriesException::class)
    fun getDelta(version: Long): List<GenericFSEntry> {
        return fsDao.getDelta(version)
    }

    @Throws(SqlQueriesException::class)
    fun getDeltaResource(version: Long): ISQLResource<GenericFSEntry?>? {
        return fsDao.getDeltaResource(version)
    }

    companion object {
        fun getSqlqueriesCreator(): SQLConnectionCreator {
            return sqlqueriesCreator
        }

        private var sqlqueriesCreator: SQLConnectionCreator = object : SQLConnectionCreator {
            override fun createConnection(fileSyncDatabaseManager: FileSyncDatabaseManager, uuid: String?): ISQLQueries {
                val f = File(fileSyncDatabaseManager.createWorkingPath().toString() + FileSyncStrings.DB_FILENAME)
                return SQLQueries(SQLConnector.createSqliteConnection(f), true, RWLock(), SqlResultTransformer.sqliteResultSetTransformer())
            }
        }

        fun setSqlqueriesCreator(sqlqueriesCreator: SQLConnectionCreator) {
            FileSyncDatabaseManager.sqlqueriesCreator = sqlqueriesCreator
        }

        @Throws(ClassNotFoundException::class, SQLException::class)
        fun createSqliteConnection(): Connection? {
            Class.forName("org.sqlite.JDBC")
            return DriverManager.getConnection("jdbc:sqlite::memory:")
        }
    }


}