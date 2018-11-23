package de.miniserver

import de.mein.Lok
import de.mein.MeinRunnable
import de.mein.MeinThread
import de.mein.auth.MeinStrings
import de.mein.auth.data.MeinAuthSettings
import de.mein.auth.data.access.CertificateManager
import de.mein.auth.data.access.DatabaseManager
import de.mein.auth.tools.N
import de.mein.execute.SqliteExecutor
import de.mein.konsole.Konsole
import de.mein.sql.Hash
import de.mein.sql.RWLock
import de.mein.sql.SQLQueries
import de.mein.sql.SqlQueriesException
import de.mein.sql.conn.SQLConnector
import de.mein.sql.transform.SqlResultTransformer
import de.mein.update.VersionAnswer
import de.miniserver.data.FileRepository
import de.miniserver.http.HttpThingy
import de.miniserver.socket.BinarySocketOpener
import de.miniserver.socket.EncSocketOpener
import java.io.File
import java.io.FileInputStream
import java.nio.file.Paths
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class MiniServer @Throws(Exception::class)
constructor(config: ServerConfig) {

    private val certificateManager: CertificateManager
    private val versionAnswer: VersionAnswer
    private val threadFactory = { r: Runnable ->
        var meinThread: MeinThread? = null

        try {
            threadSemaphore.acquire()
            meinThread = threadQueue.poll()
            threadSemaphore.release()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        meinThread
    }
    internal var executorService: ExecutorService? = null
    internal val fileRepository: FileRepository
    private var encSocketOpener: EncSocketOpener? = null
    private var binarySocketOpener: BinarySocketOpener? = null

    val certificate: X509Certificate
        get() = certificateManager.myX509Certificate

    var indexFile: File

    init {
        val workingDir = File(config.workingDirectory!!)
        workingDir.mkdir()
        val dbFile = File(workingDir, "db.db")
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
        certificateManager = CertificateManager(workingDir, sqlQueries, 2048)

        // loading and hashing files
        val filesDir = File(workingDir, DIR_FILES_NAME)
        filesDir.mkdir()
        versionAnswer = VersionAnswer()
        fileRepository = FileRepository()


        //looking for jar, apk and their appropriate version.txt
        Lok.debug("looking for files in ${filesDir.absolutePath}")
        for (f in filesDir.listFiles { f -> f.isFile && (f.name.endsWith(".jar") || f.name.endsWith(".apk")) }!!) {
            val hash: String
            val variant: String
            val version: Long?
            val propertiesFile: File

            propertiesFile = File(filesDir, f.name + MeinStrings.update.INFO_APPENDIX)
            Lok.debug("reading binary: " + f.absolutePath)
            Lok.debug("reading  props: " + propertiesFile.absolutePath)
            hash = Hash.sha256(FileInputStream(f))
            val properties = Properties()
            properties.load(FileInputStream(propertiesFile))

            variant = properties.getProperty("variant")
            version = java.lang.Long.valueOf(properties.getProperty("version"))
            fileRepository.addEntry(hash, f)
            versionAnswer.addEntry(hash, variant, version, f.length())
        }
        val htmlDir = File(workingDir, DIR_HTML_NAME)
        htmlDir.mkdir()
        indexFile = File(htmlDir,"index.html")


    }



    fun execute(runnable: MeinRunnable) {

        try {
            if (executorService == null || executorService != null && (executorService!!.isShutdown || executorService!!.isTerminated))
                executorService = Executors.newCachedThreadPool(threadFactory)
            threadSemaphore.acquire()
            threadQueue.add(MeinThread(runnable))
            threadSemaphore.release()
            executorService!!.execute(runnable)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

    }

    private lateinit var httpSocketOpener: HttpThingy

    fun start() {
        // starting sockets
        encSocketOpener = EncSocketOpener(certificateManager, MeinAuthSettings.UPDATE_MSG_PORT, this, versionAnswer)
        execute(encSocketOpener!!)
        binarySocketOpener = BinarySocketOpener(MeinAuthSettings.UPDATE_BINARY_PORT, this, fileRepository)
        execute(binarySocketOpener!!)

        httpSocketOpener = HttpThingy(9888, this, fileRepository)
//        execute(httpSocketOpener)
        httpSocketOpener.start()


        Lok.debug("I am up!")
    }

    fun shutdown() {
        executorService!!.shutdown()
        binarySocketOpener!!.onShutDown()
        N.r { binarySocketOpener!!.onShutDown() }
        N.r { encSocketOpener!!.onShutDown() }
        try {
            executorService!!.awaitTermination(5, TimeUnit.SECONDS)
            Lok.debug("is down: " + executorService!!.isShutdown)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

    }

    companion object {
        private val DEFAULT_WORKING_DIR = File("miniserver.w")
        const val DIR_FILES_NAME = "files"
        const val DIR_HTML_NAME = "html"
        private val threadSemaphore = Semaphore(1, true)
        private val threadQueue = LinkedList<MeinThread>()


        @JvmStatic
        fun main(arguments: Array<String>) {
            Lok.debug("starting in: " + File("").absolutePath)
            val konsole = Konsole(ServerConfig())
            konsole.optional("-create-cert", "name of the certificate", { result, args -> result.certName = args[0] }, Konsole.dependsOn("-pubkey", "-privkey"))
                    .optional("-cert", "path to certificate", { result, args -> result.certPath = Konsole.check.checkRead(args[0]) }, Konsole.dependsOn("-pubkey", "-privkey"))
                    .optional("-pubkey", "path to public key", { result, args -> result.pubKeyPath = Konsole.check.checkRead(args[0]) }, Konsole.dependsOn("-privkey", "-cert"))
                    .optional("-privkey", "path to private key", { result, args -> result.privKeyPath = Konsole.check.checkRead(args[0]) }, Konsole.dependsOn("-pubkey", "-cert"))
                    .optional("-dir", "path to working directory") { result, args -> result.workingDirectory = args[0] }
            //                .optional("-files", "tuples of files, versions and names. eg: '-files -f1 v1 n1 -f2 v12 n2'", ((result, args) -> {
            //                    if (args.length % 2 != 0)
            //                        throw new Konsole.ParseArgumentException("even number of entries");
            //                    for (int i = 0; i < args.length; i += 2) {
            //                        result.addEntry(Konsole.check.checkRead(args[0]), args[i+2],Konsole.check.checkRead(args[i + 1]));
            //                    }
            //                }));
            try {
                konsole.handle(arguments)
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
            var workingDir = DEFAULT_WORKING_DIR
            if (config.workingDirectory != null) {
                val path = Paths.get(config.workingDirectory)
                workingDir = path.toFile()
            }
            config.workingDirectory = workingDir.absolutePath
            Lok.debug("dir: " + workingDir.absolutePath)
            Lok.debug("port: " + MeinAuthSettings.UPDATE_BINARY_PORT + ", msgport: " + MeinAuthSettings.UPDATE_MSG_PORT)
            var miniServer: MiniServer? = null
            try {
                miniServer = MiniServer(config)
                miniServer.start()
                RWLock().lockWrite().lockWrite()
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    }
}
