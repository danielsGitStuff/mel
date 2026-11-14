package de.mel.location.data.db

import de.mel.auth.data.access.FileRelatedManager
import de.mel.execute.SqliteExecutor
import de.mel.location.data.LocationSettings
import de.mel.location.data.LocationStrings
import de.mel.sql.ISQLQueries
import de.mel.sql.RWLock
import de.mel.sql.SQLQueries
import de.mel.sql.conn.SQLConnector
import de.mel.sql.transform.SqlResultTransformer
import java.io.File
import java.io.InputStream

class LocationDatabaseManager(
    workingDirectory: File,
    serviceUuid: String,
    settingsCfg: LocationSettings?
) : FileRelatedManager(workingDirectory) {

    val sqlQueries: ISQLQueries = sqlqueriesCreator(this, serviceUuid)
    val locationDao: LocationDao
    val settings: LocationSettings

    init {
        val executor = SqliteExecutor(sqlQueries.sqlConnection)
        if (!executor.checkTablesExist("locations")) {
            val stream = sqlInputStreamInjector()
                ?: error("Missing resource de/mel/location/location.sql")
            executor.executeStream(stream)
            hadToInitialize = true
        }
        locationDao = LocationDao(sqlQueries)
        val settingsFile = File(workingDirectory, LocationStrings.SETTINGS_FILENAME)
        settings = LocationSettings.load(settingsFile, settingsCfg)
    }

    companion object {
        @JvmStatic
        var sqlqueriesCreator: (LocationDatabaseManager, String) -> ISQLQueries = { mgr, _ ->
            val queries = SQLQueries(
                SQLConnector.createSqliteConnection(File(mgr.createWorkingPath() + LocationStrings.DB_FILENAME)),
                true,
                RWLock(),
                SqlResultTransformer.sqliteResultSetTransformer()
            )
            queries.sqlConnection.prepareStatement("PRAGMA synchronous=OFF").execute()
            queries
        }

        @JvmStatic
        var sqlInputStreamInjector: () -> InputStream? = {
            LocationDatabaseManager::class.java.classLoader.getResourceAsStream("de/mel/location/location.sql")
        }
    }
}
