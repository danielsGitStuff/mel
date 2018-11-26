package de.miniserver

import de.mein.Lok
import de.mein.konsole.Konsole
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.security.SecureRandom
import java.util.*
import kotlin.system.exitProcess

class Deploy(private val deploySettings: DeploySettings) {
    var props: Properties = Properties()
    fun run() {

        val secretFile = File(deploySettings.secretFile)
        props.load(secretFile.inputStream())
        fetch()
        val p = File(props.getProperty("projectRootDir")).absolutePath
        val projectRootDir = if (p.endsWith(File.separator)) p else p + File.separator
        val gradle = "${projectRootDir}gradlew"
        val workingDir = File(projectRootDir)
        Lok.debug("working dir ${workingDir.absolutePath}")

//        runProcesses("test",
//                Processor(gradle, ":auth:test"),
//                Processor(gradle, ":calendar:test"),
//                Processor(gradle, ":contacts:test"),
//                Processor(gradle, ":drive:test"),
//                Processor(gradle, ":konsole:test"),
//                Processor(gradle, ":miniserver:test"),
//                Processor(gradle, ":serialize:test"),
//                Processor(gradle, ":sql:test"))

        runProcesses("build",
                Processor(gradle, ":fxbundle:buildFxJar"),
                Processor(gradle, ":app:assembleDebug"),
                Processor(gradle, ":miniserver:buildServerJar"))

//        runProcesses("debug", Processor(gradle, ":miniserver:buildServerJar"))

        val serverDir = File("${projectRootDir}miniserver${File.separator}server")
        Lok.debug("setting up deployed dir ${serverDir.absolutePath}")
        if (serverDir.exists()) {
            val stopFile = File(serverDir, "stop.input")
            if (stopFile.exists() && stopFile.canWrite()) {
                GlobalScope.launch {
                    withTimeout(1000) {
//                        stopFile.outputStream().write("stop".toByteArray())
//                        stopFile.outputStream().close()
                    }
                }
            }
            val secretDir = File(serverDir, "secret")
            var secretMovedDir = File(projectRootDir, SecureRandom().nextInt().toString())
            if (secretDir.exists()) {
                secretDir.renameTo(secretMovedDir)
            }
            serverDir.deleteRecursively()
            if (secretMovedDir.exists()) {
                serverDir.mkdirs()
                secretMovedDir.renameTo(secretDir)
            }
        }
        serverDir.mkdirs()
        val serverFilesDir = File(serverDir, "files")
        serverFilesDir.mkdirs()
        val target = serverFilesDir.absolutePath
        val workPath = workingDir.absolutePath + File.separator
//        runProcesses("debug copy",
//                Processor("/bin/sh", "-c", "cp \"${workPath}miniserver/build/libs/\"* \"${serverDir.absolutePath}\""))

        runProcesses("copying",
                Processor("/bin/sh", "-c", "cp \"${workPath}miniserver/build/libs/\"* \"${serverDir.absolutePath}\""),
                Processor("/bin/sh", "-c", "cp \"${workPath}fxbundle/build/libs/\"* \"$target\""),
                Processor("/bin/sh", "-c", "cp \"${workPath}app/build/outputs/apk/debug/\"* \"$target\""),
                Processor("/bin/sh", "-c", "rm \"$target/output.json\""
                ))

        // start first jar in serverDir
        val serverJar = serverDir.listFiles().first()
        Lok.debug("starting server jar ${serverJar.absolutePath}")
        Processor("java", "-jar", serverJar.absolutePath, "-http", "-pipes", "-dir", serverDir.absolutePath, "&", "detach").run(false)
        Lok.debug("command succeeded")
        Lok.debug("done")
        exitProcess(0)
    }

    private fun runProcesses(name: String, vararg processors: Processor) {
        processors.find { processor -> !processor.run().success }?.let {
            it.errorLines?.forEach { Lok.debug(it) }
            error("'$name' failed: ${it.cmdLine}")
        }
        Lok.debug("'$name' successful")

    }

    private fun fetch() {
        Lok.debug("skipping fetch for now...")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val konsole = Konsole<DeploySettings>(DeploySettings())
                    .mandatory("-secret", "path to secret file, wich contains passwords and so on") { result, args -> result.secretFile = args[0] }
            konsole.handle(args)
            val deploy = Deploy(konsole.result)
            deploy.run()
            Lok.debug("deploy finished")
        }
    }
}

