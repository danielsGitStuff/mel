package de.miniserver

import de.mein.Lok
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.security.SecureRandom
import java.util.*
import kotlin.system.exitProcess

class Deploy(val miniServer: MiniServer, private val deploySettings: DeploySettings) {
    var props: Properties = Properties()
    fun run() {

        val secretFile = File(deploySettings.secretFile)
        props.load(secretFile.inputStream())
        fetch()
        val p = File(props.getProperty("projectRootDir")).absolutePath
        val projectRootDir = File(p)
        val miniServerDir = File(projectRootDir, "miniserver")
        val serverDir = File(miniServerDir, "server")//File("${projectRootDir}miniserver${File.separator}server")
        val gradle = File(projectRootDir, "gradlew")
        Lok.debug("working dir ${projectRootDir.absolutePath}")

        Processor.runProcesses("pull from git", Processor("git", "pull"))

        val updateCertFile = File(File(File(serverDir, "secret"), "socket"), "cert.cert")
        // holy shit
        val updateCertTarget = File(File(File(File(File(File(File(File(projectRootDir, "auth"), "src"), "main"), "resources"), "de"), "mein"), "auth"), "update.server.cert")
        Processor.runProcesses("copy update cert", Processor("cp", updateCertFile.absolutePath, updateCertTarget.absolutePath))

        Processor.runProcesses("run tests",
                Processor(gradle.absolutePath, ":auth:test"),
                Processor(gradle.absolutePath, ":calendar:test"),
                Processor(gradle.absolutePath, ":contacts:test"),
                Processor(gradle.absolutePath, ":drive:test"),
                Processor(gradle.absolutePath, ":konsole:test"),
                Processor(gradle.absolutePath, ":miniserver:test"),
                Processor(gradle.absolutePath, ":serialize:test"),
                Processor(gradle.absolutePath, ":sql:test"))

        Processor.runProcesses("build",
                Processor(gradle.absolutePath, ":fxbundle:buildFxJar"),
                Processor(gradle.absolutePath, ":app:assemble"),
                Processor(gradle.absolutePath, ":miniserver:buildServerJar"))

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
            val secretMovedDir = File(projectRootDir, SecureRandom().nextInt().toString())
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

        Processor.runProcesses("copying",
                Processor("cp", "${projectRootDir.absolutePath}/miniserver/build/libs/*", serverDir.absolutePath),
                Processor("cp", "${projectRootDir.absolutePath}/fxbundle/build/libs/*", target),
                Processor("cp", "${projectRootDir.absolutePath}/app/build/outputs/apk/debug/*", target),
                Processor("cp", "${projectRootDir.absolutePath}/app/build/outputs/apk/release/*", target),
                Processor("rm", "$target/output.json"
                ))

        // start first jar in serverDir
        val serverJar = serverDir.listFiles().first()
        Lok.debug("starting server jar ${serverJar.absolutePath}")
        Processor("java", "-jar", serverJar.absolutePath, "-http", "-pipes", "-dir", serverDir.absolutePath, "&", "detach").run(false)
        Lok.debug("command succeeded")
        Lok.debug("done")
        exitProcess(0)
    }


    private fun fetch() {
        Lok.debug("skipping fetch for now...")
    }
}

