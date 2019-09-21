package de.miniserver.blog

import de.mein.auth.data.access.FileRelatedManager
import de.mein.execute.SqliteExecutor
import de.mein.serverparts.visits.VisitsDao
import de.mein.sql.RWLock
import de.mein.sql.SQLQueries
import de.mein.sql.conn.SQLConnector
import de.mein.sql.transform.SqlResultTransformer
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset

class BlogDatabaseManager(workingDirectory: File) : FileRelatedManager(workingDirectory) {
    private val sqlQueries: SQLQueries
    val blogDao: BlogDao
    val visitsDao: VisitsDao

    init {
        val parent = File(createWorkingPath())
        val dbFile = File(parent, DB_FILE_NAME)
        sqlQueries = SQLQueries(SQLConnector.createSqliteConnection(dbFile), true, RWLock(), SqlResultTransformer.sqliteResultSetTransformer())
        blogDao = BlogDao(sqlQueries)
        visitsDao = VisitsDao(sqlQueries)

        val sqliteExecutor = SqliteExecutor(sqlQueries.sqlConnection)
        if (!sqliteExecutor.checkTablesExist("blogentry", "visits")) {
            sqliteExecutor.executeStream(BlogDatabaseManager::class.java.getResourceAsStream("/de/miniserver/blog/blog.sql"))
            hadToInitialize = true
            val dummyEntry = BlogEntry()
            dummyEntry.text.v("this is a placeholder")
            dummyEntry.title.v("title placeholder")
            dummyEntry.published.v(true)
            dummyEntry.timestamp.v(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
            blogDao.insert(dummyEntry)
            visitsDao.createTable()
        }
    }

    companion object {
        val DB_FILE_NAME = "blog.db"
    }
}