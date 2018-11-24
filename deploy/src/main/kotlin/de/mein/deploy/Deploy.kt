package de.mein.deploy

import de.mein.Lok
import de.mein.konsole.Konsole
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
        runProcesses("build", Processor(gradle, ":fxbundle:buildFxJar"),
                Processor(gradle, ":app:assembleDebug"),
                Processor(gradle, ":miniserver:buildServerJar"))

        runProcesses("test",
                Processor(gradle, ":sql:test"),
                Processor(gradle, ":auth:test"),
                Processor(gradle, ":calendar:test"),
                Processor(gradle, ":contacts:test"),
                Processor(gradle, ":drive:test"),
                Processor(gradle, ":konsole:test"),
                Processor(gradle, ":miniserver:test"),
                Processor(gradle, ":serialize:test"),
                Processor(gradle, ":sql:test"))


        val serverDir = File("deploy${File.separator}server")
        serverDir.deleteRecursively()
    }

    private fun runProcesses(name: String, vararg processors: Processor) {
        processors.find { processor -> !processor.run().success }?.let {
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

