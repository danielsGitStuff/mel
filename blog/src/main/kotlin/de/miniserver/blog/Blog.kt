package de.miniserver.blog

import de.mein.auth.data.access.CertificateManager
import de.mein.auth.data.access.DatabaseManager
import de.mein.execute.SqliteExecutor
import de.mein.konsole.Konsole
import de.mein.sql.RWLock
import de.mein.sql.SQLQueries
import de.mein.sql.SqlQueriesException
import de.mein.sql.conn.SQLConnector
import de.mein.sql.transform.SqlResultTransformer
import java.io.File

class Blog(val settings: BlogSettings) {

    companion object {
        @JvmStatic
        fun main(arguments: Array<String>) {
            val currentDir = System.getProperty("user.dir")
            println("Working Directory = $currentDir")

            val blogDir = File(currentDir, "blog")
            blogDir.mkdirs()
            val settings = BlogSettings.loadBlogSettings(blogDir)
            settings.blogDir = blogDir

            val konsole = Konsole(settings)
                    .optional("-port", "port of incoming ssl connections", { result, args -> result.port = args[0].toInt() })
//            konsole.optional("-cert","path to cert",{ result, args -> result.certPath })

            var blog: Blog? = null
            try {
                settings.save()
                blog = Blog(settings)
                blog.start()
                RWLock().lockWrite().lockWrite()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var certificateManager: CertificateManager
    private var blogThingy: BlogThingy? = null

    init {
        fun setupAuthSqlQueries(dir: File): SQLQueries {
            val dbFile = File(dir, "auth.db")
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
                val resourceStream = DatabaseManager::class.java.getResourceAsStream("/de/mein/auth/sql.sql")
                sqliteExecutor.executeStream(resourceStream)
            }
            return sqlQueries
        }

        val authSqlQueries = setupAuthSqlQueries(settings.blogDir!!)
        certificateManager = CertificateManager(settings.blogDir, authSqlQueries, BlogSettings.DEFAULT_KEY_SIZE)
    }

    private fun start() {
        blogThingy = BlogThingy(settings, certificateManager.sslContext)
        blogThingy!!.start()
    }
}