package de.mel.sms.data.db

import de.mel.auth.data.access.FileRelatedManager
import de.mel.execute.SqliteExecutor
import de.mel.sms.data.SmsSettings
import de.mel.sms.data.SmsStrings
import de.mel.sql.ISQLQueries
import de.mel.sql.RWLock
import de.mel.sql.SQLQueries
import de.mel.sql.conn.SQLConnector
import de.mel.sql.transform.SqlResultTransformer
import java.io.File
import java.io.InputStream

class SmsDatabaseManager(
    workingDirectory: File,
    serviceUuid: String,
    settingsCfg: SmsSettings?
) : FileRelatedManager(workingDirectory) {

    val sqlQueries: ISQLQueries = sqlqueriesCreator(this, serviceUuid)
    val smsDao: SmsDao
    val settings: SmsSettings

    init {
        val executor = SqliteExecutor(sqlQueries.sqlConnection)
        if (!executor.checkTablesExist("sms_messages")) {
            val stream = sqlInputStreamInjector()
                ?: error("Missing resource de/mel/sms/sms.sql")
            executor.executeStream(stream)
            hadToInitialize = true
        }
        smsDao = SmsDao(sqlQueries)
        val settingsFile = File(workingDirectory, SmsStrings.SETTINGS_FILENAME)
        settings = SmsSettings.load(settingsFile, settingsCfg)
    }

    companion object {
        @JvmStatic
        var sqlqueriesCreator: (SmsDatabaseManager, String) -> ISQLQueries = { mgr, _ ->
            val queries = SQLQueries(
                SQLConnector.createSqliteConnection(File(mgr.createWorkingPath() + SmsStrings.DB_FILENAME)),
                true,
                RWLock(),
                SqlResultTransformer.sqliteResultSetTransformer()
            )
            queries.sqlConnection.prepareStatement("PRAGMA synchronous=OFF").execute()
            queries
        }

        @JvmStatic
        var sqlInputStreamInjector: () -> InputStream? = {
            SmsDatabaseManager::class.java.classLoader.getResourceAsStream("de/mel/sms/sms.sql")
        }
    }
}
