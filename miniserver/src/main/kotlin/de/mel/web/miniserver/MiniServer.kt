package de.mel.web.miniserver

import de.mel.Lok
import de.mel.LokImpl
import de.mel.MelRunnable
import de.mel.MelThread
import de.mel.auth.MelStrings
import de.mel.auth.data.access.CertificateManager
import de.mel.auth.tools.DBLokImpl
import de.mel.auth.tools.N
import de.mel.konsole.Konsole
import de.mel.sql.Hash
import de.mel.sql.RWLock
import de.mel.update.VersionAnswer
import de.mel.update.VersionAnswerEntry
import de.mel.web.miniserver.data.FileEntry
import de.mel.web.miniserver.data.FileRepository
import de.mel.web.serverparts.HttpThingy
import de.mel.web.miniserver.http.HttpsThingy
import de.mel.web.miniserver.input.InputPipeReader
import de.mel.web.miniserver.socket.BinarySocketOpener
import de.mel.web.miniserver.socket.EncSocketOpener
import de.mel.web.serverparts.SetupHelper
import java.io.File
import java.io.FileInputStream
import java.lang.management.ManagementFactory
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class MiniServer @Throws(Exception::class)
constructor(val config: ServerConfig) {
    val startTime = MDate()
    private val socketCertificateManager: CertificateManager
    val httpCertificateManager: CertificateManager
    private val versionAnswer: VersionAnswer
    private val threadFactory = { r: Runnable ->
        var melThread: MelThread? = null

        try {
            threadSemaphore.acquire()
            melThread = threadQueue.poll()
            threadSemaphore.release()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        melThread
    }
    internal var executorService: ExecutorService? = null
    internal val fileRepository: FileRepository
    private var encSocketOpener: EncSocketOpener? = null
    private var binarySocketOpener: BinarySocketOpener? = null
    private var httpsSocketOpener: HttpsThingy? = null
    private var httpSocketOpener: HttpThingy? = null

    val certificate: X509Certificate
        get() = socketCertificateManager.myX509Certificate


    val secretProperties = Properties()

    var secretPropFile: File

    val workingDirectory: File = config.workingDirectory!!

    init {
        workingDirectory.mkdir()


        val secretDir = File(workingDirectory, "secret")
        val secretHttpDir = File(secretDir, "http")
        val secretSocketDir = File(secretDir, "socket")
        secretDir.mkdirs()
        secretHttpDir.mkdirs()
        secretSocketDir.mkdirs()
        secretPropFile = File(secretDir, "secret.properties")
        if (!secretPropFile.exists()) {
            // create a default secret properties file and exit
            secretProperties["# this is for signing the apk file"] = ""
            secretProperties["storePassword"] = "type something secure here"
            secretProperties["keyPassword"] = "type something secure here"
            // stuff above is apk signing stuff, stuff below is general
            secretProperties["password"] = "type something secure here"
            secretProperties["buildPassword"] = "type something secure here"
            secretProperties["projectRootDir"] = "absolute path to project root directory goes here"
            secretProperties["storeFile"] = "path to the jks store used for signing your apk"
            secretProperties["restartCommand"] = "command that restarts the miniserver application. see readme for more information"
            val comments = "this is a generated example. please change the values to make your setup secure.\n" +
                    ""
            secretProperties.store(secretPropFile.bufferedWriter(), comments)
            error("secret properties file not found at: ${secretPropFile.absolutePath}.\n" +
                    "I generated one. Please edit it and start again.")
        }
        secretProperties.load(secretPropFile.inputStream())


        //setup socket certificate manager first
        val socketSqlQueries = SetupHelper.setupMelAuthSqlqueries(secretSocketDir)
        socketCertificateManager = CertificateManager(secretSocketDir, socketSqlQueries, config.keySize)

        if (socketCertificateManager.hadToInitialize()) {
            // new keys -> copy
            if (!File(secretDir, "cert.cert").exists())
                Processor.runProcesses("copying cert.cert from socket/ to http/")
            if (!File(secretDir, "pk.key").exists())
                Processor.runProcesses("copying pk.key from socket/ to http/")
            if (!File(secretDir, "pub.key").exists())
                Processor.runProcesses("copying pub.key from socket/ to http/")
//            Processor.runProcesses("copy keys after init",
//                    Processor("cp", File(secretSocketDir, "cert.cert").absolutePath, secretHttpDir.absolutePath),
//                    Processor("cp", File(secretSocketDir, "pk.key").absolutePath, secretHttpDir.absolutePath),
//                    Processor("cp", File(secretSocketDir, "pub.key").absolutePath, secretHttpDir.absolutePath))
        }
        val httpSqlQueries = SetupHelper.setupMelAuthSqlqueries(secretHttpDir)
        httpCertificateManager = CertificateManager(secretHttpDir, httpSqlQueries, config.keySize)

        // loading and hashing files
        val filesDir = File(workingDirectory, DIR_FILES_NAME)
        filesDir.mkdir()
        versionAnswer = VersionAnswer()
        fileRepository = FileRepository()


        //looking for jar, apk and their appropriate version.txt
        Lok.info("looking for files in ${filesDir.absolutePath}")
        N.forEachIgnorantly(filesDir.listFiles { f -> f.isFile && (f.name.endsWith(".jar") || f.name.endsWith(".apk")) }) { f ->
            val hash: String = Hash.sha256(FileInputStream(f))
            val propertiesFile = File(filesDir, f.name + MelStrings.update.INFO_APPENDIX)

            if (!propertiesFile.exists())
                return@forEachIgnorantly
            Lok.info("reading binary: " + f.absolutePath)
            Lok.info("reading  props: " + propertiesFile.absolutePath)
            val properties = Properties()
            properties.load(FileInputStream(propertiesFile))

            val variant = properties.getProperty("variant")
            val version = properties.getProperty("version")
            val commit = properties.getProperty("commit")
            val mirrorGithub: String? = properties.getProperty(MelStrings.update.GITHUB, null)

            val size = f.length()


            val mirrors = mutableListOf<String>()
            if (mirrorGithub != null)
                mirrors.add(mirrorGithub)
            val versionAnswerEntry = VersionAnswerEntry(variant = variant, hash = hash, commit = commit, version = version, length = size, mirrors = mirrors)
            versionAnswer.addEntry(versionAnswerEntry)

            val fileEntry = FileEntry(hash = hash, file = f, variant = variant, size = size, version = version, commit = commit, mirrors = mirrors)
            fileRepository += fileEntry
        }
        fileRepository.sort()
    }

    fun execute(runnable: MelRunnable) {
        try {
            if (executorService == null || executorService != null && (executorService!!.isShutdown || executorService!!.isTerminated))
                executorService = Executors.newCachedThreadPool(threadFactory)
            threadSemaphore.acquire()
            threadQueue.add(MelThread(runnable))
            threadSemaphore.release()
            executorService!!.execute(runnable)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }


    var inputReader: InputPipeReader? = null


    fun start() {
        //setup pipes
        if (config.pipes) {
            inputReader = InputPipeReader.create(config.workingDirectory!!, InputPipeReader.STOP_FILE_NAME)
        }

        // starting sockets
        encSocketOpener = EncSocketOpener(socketCertificateManager, config.authPort, this, versionAnswer)
        execute(encSocketOpener!!)
        binarySocketOpener = BinarySocketOpener(config.transferPort, this, fileRepository)
        execute(binarySocketOpener!!)

        config.httpsPort?.let {
            httpsSocketOpener = HttpsThingy(it, this, fileRepository)
            httpsSocketOpener?.start()
        }
        config.httpPort?.let {
            httpSocketOpener = HttpThingy(it, config.httpsPort)
            httpSocketOpener?.start()
        }

        Lok.info("I am up!")
    }

    fun shutdown() {
        Lok.info("shutting down...")
        executorService!!.shutdown()
        binarySocketOpener!!.onShutDown()
        httpsSocketOpener?.stop()
        httpSocketOpener?.stop()
        N.r { binarySocketOpener!!.onShutDown() }
        N.r { encSocketOpener!!.onShutDown() }
        if (config.pipes) {
            inputReader!!.stop()
        }
        try {
            executorService!!.awaitTermination(5, TimeUnit.SECONDS)
            Lok.info("is down: " + executorService!!.isShutdown)
            exitProcess(0)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        Lok.info("bye...")
    }

    fun reboot(serverDir: File, serverJar: File) {
        Lok.info("doing a reboot...")
        if (config.restartCommand.isEmpty()) {
            Lok.info("starting server jar ${serverJar.absolutePath}")
            val runtimeMxBean = ManagementFactory.getRuntimeMXBean()
            val vmArguments = runtimeMxBean.inputArguments
            vmArguments.forEach { a -> Lok.info("jvm arg: $a") }
            val commands = mutableListOf<String>()
            commands.add("java")
            commands.addAll(vmArguments)
            commands.addAll(listOf("-jar", serverJar.absolutePath, "-dir", serverDir.absolutePath))
            if (httpSocketOpener != null) {
                httpSocketOpener?.stop()
                commands.add("-http")
                if (config.httpPort != null)
                    commands.add(config.httpPort.toString())
            }
            if (httpsSocketOpener != null) {
                httpsSocketOpener?.stop()
                commands.add("-https")
                if (config.httpsPort != null)
                    commands.add(config.httpsPort.toString())
            }
//        commands.addAll(listOf("&", "detach"))
            Processor(*commands.toTypedArray()).run(false)
            Lok.info("command succeeded")
            Lok.info("done")
            exitProcess(0)
        } else {
            Lok.info("restarting using restartCommand: ${config.restartCommand}")
            Processor(*config.restartCommand.toTypedArray()).run(false)
            Lok.info("command executed successfully")
        }
    }

    companion object {
        const val DIR_FILES_NAME = "files"
        const val DIR_HTML_NAME = "html"
        private val threadSemaphore = Semaphore(1, true)
        private val threadQueue = LinkedList<MelThread>()


        @JvmStatic
        fun main(arguments: Array<String>) {
            val lokImpl = LokImpl().setup(30, true)
            lokImpl.setPrintDebug(false)
            Lok.setLokImpl(lokImpl)

            val konsole = Konsole(ServerConfig())
            konsole.optional("-create-cert", "name of the certificate", { result, args -> result.certName = args[0] }, Konsole.dependsOn("-pubkey", "-privkey"))
                    .optional("-cert", "path to certificate", { result, args -> result.certPath = Konsole.check.checkRead(args[0]) }, Konsole.dependsOn("-pubkey", "-privkey"))
                    .optional("-pubkey", "path to public key", { result, args -> result.pubKeyPath = Konsole.check.checkRead(args[0]) }, Konsole.dependsOn("-privkey", "-cert"))
                    .optional("-privkey", "path to private key", { result, args -> result.privKeyPath = Konsole.check.checkRead(args[0]) }, Konsole.dependsOn("-pubkey", "-cert"))
                    .optional("-dir", "path to working directory. defaults to 'server'") { result, args -> result.workingPath = args[0] }
                    .optional("-auth", "port the authentication service listens on. defaults to ${ServerConfig.DEFAULT_AUTH}.") { result, args -> result.authPort = args[0].toInt() }
                    .optional("-ft", "port the file transfer listens on. defaults to ${ServerConfig.DEFAULT_TRANSFER}.") { result, args -> result.transferPort = args[0].toInt() }
                    .optional("-http", "switches on http. optionally specifies the port. defaults to ${ServerConfig.DEFAULT_HTTP}") { result, args -> result.httpPort = if (args.isNotEmpty()) args[0].toInt() else ServerConfig.DEFAULT_HTTP }
                    .optional("-https", "switches on https. optionally specifies the port. defaults to ${ServerConfig.DEFAULT_HTTPS}") { result, args -> result.httpsPort = if (args.isNotEmpty()) args[0].toInt() else ServerConfig.DEFAULT_HTTPS }
                    .optional("-pipes-on", "disables pipes using mkfifo that can restart/stop the server when you write into them.") { result, _ -> result.pipes = true }
                    .optional("-keysize", "key length for certificate creation. defaults to 2048") { result, args -> result.keySize = args[0].toInt() }
                    .optional("-restart-command", "command that restarts the miniserver application. see readme for more information") { result, args -> if (args.size == 1) result.restartCommand.addAll(Konsole.tokenizeQuotedCommand(args[0])) else throw Konsole.KonsoleWrongArgumentsException("restard comman invalid") }
                    .optional("-keep-binaries", "keep binary files when rebuilding") { result, _ -> result.keepBinaries = true }
                    .optional("-logtodb", "store the log in a database") { result, args ->
                        if (args.isNotEmpty() && args[0] != null) {
                            val value = java.lang.Long.parseLong(args[0])
                            if (value < 0L) {
                                Lok.error("-logtodb [value] error: value was smaller the zero")
                                System.exit(-1)
                            }
                            result.preserveLogLinesInDb = value
                        } else {
                            result.preserveLogLinesInDb = 1000L
                        }
                    }
                    .optional("-debug", "prints debug information") { result, _ -> result.printDebug = true }

            var workingDirectory: File? = null
            try {
                konsole.handle(arguments)
                workingDirectory = File(konsole.result.workingPath)
                workingDirectory.mkdirs()
                val outFile = File(workingDirectory, "output.log")
                if (konsole.result.preserveLogLinesInDb > 0L) {
                    println("STORING LOG IN DATABASE!")
                    DBLokImpl.setupDBLockImpl(File(workingDirectory, "log.db"), konsole.result.preserveLogLinesInDb)
                    // todo debug - debug can be disabled here
                    Lok.getImpl().setPrintDebug(false).setup(30, true)
                }
                if (konsole.result.printDebug)
                    Lok.getImpl().setPrintDebug(true)
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
            var miniServer: MiniServer? = null
            try {
                miniServer = MiniServer(config)
                miniServer.start()
                // keep reference so the GC does not end the program
                val lock = RWLock().lockWrite()
                lock.lockWrite()
                Lok.warn("lock released. exiting...")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
