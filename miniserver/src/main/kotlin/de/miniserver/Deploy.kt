package de.miniserver

import de.mein.Lok
import de.mein.konsole.Konsole
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.*

class Deploy(private val deploySettings: DeploySettings) {
    var props: Properties = Properties()
    fun run() {
        val workingDir = File("")

        Lok.debug("working dir ${workingDir.absolutePath}")
        val secretFile = File(deploySettings.secretFile)
        props.load(secretFile.inputStream())
        fetch()
        val gradle = "./gradlew"

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

        val serverDir = File("deploy${File.separator}server")
        Lok.debug("setting up deployed dir ${serverDir.absolutePath}")
        if (serverDir.exists()) {
            val stopFile = File(serverDir, "stop.input")
            if (stopFile.exists() && stopFile.canWrite()) {
                GlobalScope.launch {
                    withTimeout(1000) {
                        stopFile.outputStream().write("stop".toByteArray())
                        stopFile.outputStream().close()
                    }
                }
            }
            serverDir.deleteRecursively()
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
//        runProcesses("launch",
//                Processor("java", "-jar", serverJar.absolutePath, "-http", "-pipes","&"))
        Processor("java", "-jar", serverJar.absolutePath, "-http", "-pipes", "-dir", serverDir.absolutePath, "&", "detach").run(false)

        //"-jar", serverJar.absolutePath, "-http", "-pipes", "&"
//        Processor("/bin/sh", "-c", "java -jar ${serverJar.absolutePath}").run(wait = true)
//        arrayOf()
//        ProcessBuilder(*arrayOf("a","b"))
//        val process = ProcessBuilder("/bin/sh", "-c", "java -jar ${serverJar.absolutePath}","&")
//                .redirectOutput(ProcessBuilder.Redirect.PIPE)
//                .redirectError(ProcessBuilder.Redirect.PIPE).start()
//        process.waitFor()
//        val exit = process.exitValue()
//        process.inputStream.bufferedReader().lines().forEach { println(it) }
//        if (exit != 0) {
//            Lok.debug("command failed with $exit")
//            process.inputStream.bufferedReader().lines().forEach { Lok.error(it) }
//            process.errorStream.bufferedReader().lines().forEach { Lok.error(it) }
//            error("failed")
//        }
        Lok.debug("command succeeded")

        Lok.debug("done")
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

