package de.miniserver

import de.mein.Lok
import de.mein.MeinRunnable
import de.mein.MeinThread
import de.mein.auth.MeinStrings
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
import de.miniserver.http.HttpsThingy
import de.miniserver.input.InputPipeReader
import de.miniserver.socket.BinarySocketOpener
import de.miniserver.socket.EncSocketOpener
import java.io.File
import java.io.FileInputStream
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class MiniServer @Throws(Exception::class)
constructor(private val config: ServerConfig) {

    val certificateManager: CertificateManager
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


    val secretProperties = Properties()

    var secretPropFile: File

    init {
        val workingDir = config.workingDirectory!!
        workingDir.mkdir()


        val secretDir = File(workingDir, "secret")
        secretDir.mkdirs()
        secretPropFile = File(secretDir, "secret.properties")
        if (!secretPropFile.exists()) {
            error("secret properties file not found at: ${secretPropFile.absolutePath}")
        }
        secretProperties.load(secretPropFile.inputStream())


        val dbFile = File(secretDir, "db.db")
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

        certificateManager = CertificateManager(secretDir, sqlQueries, config.keySize)

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

    private lateinit var httpsSocketOpener: HttpsThingy

    private var inputReader: InputPipeReader? = null

    fun start() {
        //setup pipes
        if (config.pipes) {
            inputReader = InputPipeReader.create(config.workingDirectory!!, InputPipeReader.STOP_FILE_NAME)
        }

        // starting sockets
        encSocketOpener = EncSocketOpener(certificateManager, config.authPort, this, versionAnswer)
        execute(encSocketOpener!!)
        binarySocketOpener = BinarySocketOpener(config.transferPort, this, fileRepository)
        execute(binarySocketOpener!!)

        config.httpPort?.let {
            httpsSocketOpener = HttpsThingy(it, this, fileRepository)
            httpsSocketOpener.start()
        }

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
        const val DIR_FILES_NAME = "files"
        const val DIR_HTML_NAME = "html"
        private val threadSemaphore = Semaphore(1, true)
        private val threadQueue = LinkedList<MeinThread>()


        @JvmStatic
        fun main(arguments: Array<String>) {
            val konsole = Konsole(ServerConfig())
            konsole.optional("-create-cert", "name of the certificate", { result, args -> result.certName = args[0] }, Konsole.dependsOn("-pubkey", "-privkey"))
                    .optional("-cert", "path to certificate", { result, args -> result.certPath = Konsole.check.checkRead(args[0]) }, Konsole.dependsOn("-pubkey", "-privkey"))
                    .optional("-pubkey", "path to public key", { result, args -> result.pubKeyPath = Konsole.check.checkRead(args[0]) }, Konsole.dependsOn("-privkey", "-cert"))
                    .optional("-privkey", "path to private key", { result, args -> result.privKeyPath = Konsole.check.checkRead(args[0]) }, Konsole.dependsOn("-pubkey", "-cert"))
                    .optional("-dir", "path to working directory") { result, args -> result.workingPath = args[0] }
                    .optional("-auth", "port the authentication service listens on. defaults to ${ServerConfig.DEFAULT_AUTH}.") { result, args -> result.authPort = args[0].toInt() }
                    .optional("-ft", "port the file transfer listens on. defaults to ${ServerConfig.DEFAULT_TRANSFER}.") { result, args -> result.transferPort = args[0].toInt() }
                    .optional("-http", "starts the http server. specifies the port it listens on.") { result, args -> result.httpPort = if (args.isNotEmpty()) args[0].toInt() else ServerConfig.DEFAULT_HTTP }
                    .optional("-pipes", "sets up pipes using mkfifo that can restart/stop the server when you write into them.") { result, _ -> result.pipes = true }
                    .optional("-keysize", "key length for certificate creation. defaults to 2048") { result, args -> result.keySize = args[0].toInt() }


            var workingDirectory: File? = null
            try {
                konsole.handle(arguments)
                workingDirectory = File(konsole.result.workingPath)
                val outFile = File(workingDirectory, "output.log")
                if (outFile.exists())
                    outFile.delete()
                val outWriter = outFile.outputStream().bufferedWriter()
                Lok.setLokListener { line ->
                    outWriter.append(line)
                    outWriter.newLine()
                    outWriter.flush()
                }
                Lok.debug("starting in: " + File("").absolutePath)
                Lok.debug("starting with parameters: ${arguments.fold("") { acc: String, s: String -> "$acc $s" }}")
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

            Lok.debug("dir: " + workingDirectory!!.absolutePath)
            Lok.debug("auth port: ${config.authPort}, transfer port: ${config.transferPort}, http port: ${config.httpPort}")
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
