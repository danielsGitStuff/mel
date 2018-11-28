package de.miniserver

import de.mein.Lok
import de.mein.auth.tools.N
import de.miniserver.http.BuildRequest
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.security.SecureRandom
import java.util.*

class Deploy(val miniServer: MiniServer, private val secretFile: File, val buildRequest: BuildRequest) {
    var props: Properties = Properties()
    fun run() {
        N.r { Lok.debug("run()") }
        GlobalScope.launch {
            N.r { Lok.debug("running") }
            props.load(secretFile.inputStream())
            fetch()
            val p = File(props.getProperty("projectRootDir")).absolutePath
            val projectRootDir = File(p)
            val miniServerDir = File(projectRootDir, "miniserver")
            val serverDir = File(miniServerDir, "server")//File("${projectRootDir}miniserver${File.separator}server")
            val secretDir = File(serverDir, "secret")
            val gradle = File(projectRootDir, "gradlew")
            val miniServerTarget = File(serverDir, "miniserver.jar")
            Lok.debug("working dir ${projectRootDir.absolutePath}")

            // pull
            Processor.runProcesses("pull from git", Processor("git", "pull"))
            // put keystore.properties in place
            val keyStoreFile = File(secretDir, "sign.jks")
            val keyStorePropFile = File(projectRootDir, "keystore.properties")
            val keyProps = Properties()
            try {
                keyProps["storePassword"] = miniServer.secretProperties["storePassword"]
                keyProps["keyPassword"] = miniServer.secretProperties["keyPassword"]
                keyProps["keyAlias"] = miniServer.secretProperties["keyAlias"]
                keyProps["storeFile"] = keyStoreFile.absolutePath
                keyProps.store(keyStorePropFile.outputStream(), "keep me private")
                //clean
                Processor.runProcesses("clean",
                        Processor(gradle.absolutePath, "clean"),
                        Processor(gradle.absolutePath, ":app:clean"))

                //put update server certificate in place
                val updateCertFile = File(File(File(serverDir, "secret"), "socket"), "cert.cert")
                // holy shit
                val updateCertTarget = File(File(File(File(File(File(File(File(projectRootDir, "auth"), "src"), "main"), "resources"), "de"), "mein"), "auth"), "update.server.cert")
                Processor.runProcesses("copy update cert", Processor("cp", updateCertFile.absolutePath, updateCertTarget.absolutePath))

                // tests take a shitload of time on that stupid machine
//        Processor.runProcesses("run tests",
//                Processor(gradle.absolutePath, ":auth:test"),
//                Processor(gradle.absolutePath, ":calendar:test"),
//                Processor(gradle.absolutePath, ":contacts:test"),
//                Processor(gradle.absolutePath, ":drive:test"),
//                Processor(gradle.absolutePath, ":konsole:test"),
//                Processor(gradle.absolutePath, ":miniserver:test"),
//                Processor(gradle.absolutePath, ":serialize:test"),
//                Processor(gradle.absolutePath, ":sql:test"))

                // assemble binaries
                val processList = mutableListOf<Processor>()
                if (buildRequest.jar!!)
                    processList.add(Processor(gradle.absolutePath, ":fxbundle:buildFxJar"))
                if (buildRequest.apk!!)
                    processList.add(Processor(gradle.absolutePath, ":app:assembleRelease"))
                if (buildRequest.server!!)
                    processList.add(Processor(gradle.absolutePath, ":miniserver:buildServerJar"))
                Processor.runProcesses("assemble/build", *processList.toTypedArray())
                //todo remove
//                Processor.runProcesses("assemble/build",
//                        Processor(gradle.absolutePath, ":fxbundle:buildFxJar"),
//                        Processor(gradle.absolutePath, ":app:assemblRelease"),
//                        Processor(gradle.absolutePath, ":miniserver:buildServerJar"))
            } finally {
                if (keyStorePropFile.exists())
                    keyStorePropFile.delete()
            }
            Lok.debug("setting up deployed dir ${serverDir.absolutePath}")
            if (serverDir.exists()) {
                val secretDir = File(serverDir, "secret")
                val secretMovedDir = File(projectRootDir, SecureRandom().nextInt().toString())
                if (secretDir.exists()) {
                    secretDir.renameTo(secretMovedDir)
                }
                val miniServerBackup = File(miniServerDir,"server.backup.jar")
                var miniBackup = false
                if (!buildRequest.server!! && miniServerTarget.exists()){
                    miniServerTarget.renameTo(miniServerBackup)
                    miniBackup = true
                }
                serverDir.deleteRecursively()
                if (secretMovedDir.exists()) {
                    serverDir.mkdirs()
                    secretMovedDir.renameTo(secretDir)
                }
                if (miniBackup){
                    miniServerBackup.renameTo(miniServerTarget)
                }
            }
            serverDir.mkdirs()
            val serverFilesDir = File(serverDir, "files")
            serverFilesDir.mkdirs()
            //todo redundant?
//            if (buildRequest.cleanUp!!) {
//                Processor.runProcesses("clean files dir",
//                        Processor("/bin/sh", "-c", "rm -rf \"${serverFilesDir.absolutePath}/\"*"))
//            }
            //copy MiniServer.jar

            val processList = mutableListOf<Processor>()
            if (buildRequest.server!!) {
                val miniServerSource = File("${projectRootDir.absolutePath}/miniserver/build/libs/").listFiles().first()
                processList.add(Processor("cp", miniServerSource.absolutePath, miniServerTarget.absolutePath))
            }
            if (buildRequest.apk!!)
                processList.add(Processor("/bin/sh", "-c", "cp \"${projectRootDir.absolutePath}/app/build/outputs/apk/release/\"* \"${serverFilesDir.absolutePath}\""))
            if (buildRequest.jar!!)
                processList.add(Processor("/bin/sh", "-c", "cp \"${projectRootDir.absolutePath}/fxbundle/build/libs/\"* \"${serverFilesDir.absolutePath}\""))
            processList.add(Processor("rm", "-f", "${File(serverFilesDir, "output.json")}"))
            Processor.runProcesses("copying", *processList.toTypedArray())
            //todo remove
//            Processor.runProcesses("copying",
//                    Processor("cp", miniServerSource.absolutePath, miniServerTarget.absolutePath),
//                    Processor("/bin/sh", "-c", "cp \"${projectRootDir.absolutePath}/fxbundle/build/libs/\"* \"${serverFilesDir.absolutePath}\""),
//                    Processor("/bin/sh", "-c", "cp \"${projectRootDir.absolutePath}/app/build/outputs/apk/debug/\"* \"${serverFilesDir.absolutePath}\""),
//                    Processor("/bin/sh", "-c", "cp \"${projectRootDir.absolutePath}/app/build/outputs/apk/release/\"* \"${serverFilesDir.absolutePath}\""),
//                    Processor("rm", "-f", "${File(serverFilesDir, "output.json")}"))

            // delete stop pipe
            miniServer.inputReader?.stop()
            // restart
            miniServer.reboot(serverDir, miniServerTarget)
        }
    }


    private fun fetch() {
        Lok.debug("skipping fetch for now...")
    }
}

