package de.miniserver

import de.mel.Lok
import de.mel.LokImpl
import de.mel.MelRunnable
import de.mel.MelThread
import de.mel.auth.data.access.CertificateManager
import de.mel.auth.data.access.DatabaseManager
import de.mel.execute.SqliteExecutor
import de.mel.konsole.Konsole
import de.mel.sql.RWLock
import de.mel.sql.SQLQueries
import de.mel.sql.SqlQueriesException
import de.mel.sql.conn.SQLConnector
import de.mel.sql.transform.SqlResultTransformer
import de.miniserver.socket.EncSocketOpener
import java.io.File
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

class StaticServer(val config: StaticConfig){
    private var httpsSocketOpener: StaticHttpsThingy? = null
    private var httpSocketOpener: StaticHttpThingy? = null
    private var encSocketOpener: EncSocketOpener? = null
    internal var executorService: ExecutorService? = null
    private val socketCertificateManager: CertificateManager
    val httpCertificateManager: CertificateManager
    val workingDirectory: File = config.workingDirectory!!


    private val threadFactory = { r: Runnable ->
        var melThread: MelThread? = null

        try {
            StaticServer.threadSemaphore.acquire()
            melThread = StaticServer.threadQueue.poll()
            StaticServer.threadSemaphore.release()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        melThread
    }

    fun execute(runnable: MelRunnable) {
        try {
            if (executorService == null || executorService != null && (executorService!!.isShutdown || executorService!!.isTerminated))
                executorService = Executors.newCachedThreadPool(threadFactory)
            StaticServer.threadSemaphore.acquire()
            StaticServer.threadQueue.add(MelThread(runnable))
            StaticServer.threadSemaphore.release()
            executorService!!.execute(runnable)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }


    init {
        workingDirectory.mkdir()


        val secretDir = File(workingDirectory, "secret")
        val secretHttpDir = File(secretDir, "http")
        val secretSocketDir = File(secretDir, "socket")
        secretDir.mkdirs()
        secretHttpDir.mkdirs()
        secretSocketDir.mkdirs()

        fun setupSql(dir: File): SQLQueries {
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

        //setup socket certificate manager first
        val socketSqlQueries = setupSql(secretSocketDir)
        socketCertificateManager = CertificateManager(secretSocketDir, socketSqlQueries, config.keySize)

        if (socketCertificateManager.hadToInitialize()) {
            // new keys -> copy
            Processor.runProcesses("copy keys after init",
                    Processor("cp", File(secretSocketDir, "cert.cert").absolutePath, secretHttpDir.absolutePath),
                    Processor("cp", File(secretSocketDir, "pk.key").absolutePath, secretHttpDir.absolutePath),
                    Processor("cp", File(secretSocketDir, "pub.key").absolutePath, secretHttpDir.absolutePath))
        }
        val httpSqlQueries = setupSql(secretHttpDir)
        httpCertificateManager = CertificateManager(secretHttpDir, httpSqlQueries, config.keySize)

    }

    fun start() {

//        // starting sockets
//        encSocketOpener = EncSocketOpener(socketCertificateManager, config.authPort, this, versionAnswer)
//        execute(encSocketOpener!!)


        config.httpsPort?.let {
            httpsSocketOpener = StaticHttpsThingy(it, this)
            httpsSocketOpener?.start()
        }
        config.httpPort?.let {
            httpSocketOpener = StaticHttpThingy(it)
            httpSocketOpener?.start()
        }

        Lok.info("I am up!")
    }

    companion object {
        const val DIR_HTML_NAME = "html"
        private val threadSemaphore = Semaphore(1, true)
        private val threadQueue = LinkedList<MelThread>()


        @JvmStatic
        fun main(arguments: Array<String>) {
            Lok.setLokImpl(LokImpl().setup(30, true))
            val konsole = Konsole(StaticConfig())
            konsole.optional("-create-cert", "name of the certificate", { result, args -> result.certName = args[0] }, Konsole.dependsOn("-pubkey", "-privkey"))
                    .optional("-cert", "path to certificate", { result, args -> result.certPath = Konsole.check.checkRead(args[0]) }, Konsole.dependsOn("-pubkey", "-privkey"))
                    .optional("-pubkey", "path to public key", { result, args -> result.pubKeyPath = Konsole.check.checkRead(args[0]) }, Konsole.dependsOn("-privkey", "-cert"))
                    .optional("-privkey", "path to private key", { result, args -> result.privKeyPath = Konsole.check.checkRead(args[0]) }, Konsole.dependsOn("-pubkey", "-cert"))
                    .optional("-dir", "path to working directory. defaults to 'server'") { result, args -> result.workingPath = args[0] }
                    .optional("-auth", "port the authentication service listens on. defaults to ${ServerConfig.DEFAULT_AUTH}.") { result, args -> result.authPort = args[0].toInt() }
                    .optional("-ft", "port the file transfer listens on. defaults to ${ServerConfig.DEFAULT_TRANSFER}.") { result, args -> result.transferPort = args[0].toInt() }
                    .optional("-http", "switches on http. optionally specifies the port. defaults to ${ServerConfig.DEFAULT_HTTP}") { result, args -> result.httpPort = if (args.isNotEmpty()) args[0].toInt() else ServerConfig.DEFAULT_HTTP }
                    .optional("-https", "switches on https. optionally specifies the port. defaults to ${ServerConfig.DEFAULT_HTTPS}") { result, args -> result.httpsPort = if (args.isNotEmpty()) args[0].toInt() else ServerConfig.DEFAULT_HTTPS }
                    .optional("-pipes-off", "disables pipes using mkfifo that can restart/stop the server when you write into them.") { result, _ -> result.pipes = false }
                    .optional("-keysize", "key length for certificate creation. defaults to 2048") { result, args -> result.keySize = args[0].toInt() }
                    .optional("-restart-command", "command that restarts the miniserver application. see readme for more information") { result, args -> result.restartCommand.addAll(args) }
                    .optional("-keep-binaries", "keep binary files when rebuilding") { result, _ -> result.keepBinaries = true }
                    .mandatory("-static","path to static stuff"){result, args -> result.staticPath = args[0] }
            var workingDirectory: File? = null
            try {
                konsole.handle(arguments)
                workingDirectory = File(konsole.result.workingPath)
                workingDirectory.mkdirs()
                val outFile = File(workingDirectory, "output.log")
                Lok.info("attempting to create output.log at: ${outFile.absoluteFile.absolutePath}")
                if (outFile.exists())
                    outFile.delete()
                val outWriter = outFile.outputStream().bufferedWriter()
                Lok.setLokListener { line ->
                    outWriter.append(line)
                    outWriter.newLine()
                    outWriter.flush()
                }
                Lok.info("starting in: " + File("").absolutePath)
                Lok.info("starting with parameters: ${arguments.fold("") { acc: String, s: String -> "$acc $s" }}")
            } catch (e: Konsole.KonsoleWrongArgumentsException) {
                Lok.error(e.javaClass.simpleName + ": " + e.message)
                System.exit(1)
            } catch (e: Konsole.DependenciesViolatedException) {
                Lok.error(e.javaClass.simpleName + ": " + e.message)
                System.exit(1)
            } catch (e: Konsole.HelpException) {
                System.exit(0)
            }

            val config = konsole.result

            Lok.info("dir: " + workingDirectory!!.absolutePath)
            Lok.info("auth port: ${config.authPort}, transfer port: ${config.transferPort}, http port: ${config.httpPort}")
            var staticServer: StaticServer? = null
            try {
                staticServer = StaticServer(config)
                staticServer.start()
                RWLock().lockWrite().lockWrite()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}


