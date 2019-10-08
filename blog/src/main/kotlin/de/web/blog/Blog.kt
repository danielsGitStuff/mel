package de.web.blog

import de.mel.Lok
import de.mel.auth.data.access.CertificateManager
import de.mel.auth.data.access.DatabaseManager
import de.mel.execute.SqliteExecutor
import de.mel.konsole.Konsole
import de.mel.sql.RWLock
import de.mel.sql.SQLQueries
import de.mel.sql.SqlQueriesException
import de.mel.sql.conn.SQLConnector
import de.mel.sql.transform.SqlResultTransformer
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
            konsole.handle(arguments)
            var blog: Blog? = null
            try {
                settings.save()
                blog = Blog(settings)
                blog.start()
                val lock = RWLock().lockWrite().lockWrite()
                Lok.warn("Lock $lock released. Exiting...")
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
                val resourceStream = DatabaseManager::class.java.getResourceAsStream("/de/mel/auth/sql.sql")
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