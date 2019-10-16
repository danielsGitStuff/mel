package de.mel.web.serverparts

import de.mel.auth.data.access.DatabaseManager
import de.mel.execute.SqliteExecutor
import de.mel.sql.RWLock
import de.mel.sql.SQLQueries
import de.mel.sql.SqlQueriesException
import de.mel.sql.conn.SQLConnector
import de.mel.sql.transform.SqlResultTransformer
import java.io.File

class SetupHelper {
    companion object{
        fun setupMelAuthSqlqueries(dir: File): SQLQueries {
            val dbFile = File(dir, "db.db")
            val sqlQueries = SQLQueries(SQLConnector.createSqliteConnection(dbFile), true, RWLock(), SqlResultTransformer.sqliteResultSetTransformer())
            // turn on foreign keys
            try {
                sqlQueries.execute("PRAGMA foreign_keys = ON;", null)
            } catch (e: SqlQueriesException) {
                e.printStackTrace()
            }
            val sqliteExecutor = SqliteExecutor(sqlQueries.sqlConnection)
            if (!sqliteExecutor.checkTablesExist("servicetype", "service", "approval", "certificate")) {
                //find sql file in workingdir
                val resourceStream = DatabaseManager::class.java.getResourceAsStream("/de/mel/auth/sql.sql")
                sqliteExecutor.executeStream(resourceStream)
            }
            return sqlQueries
        }
    }
}