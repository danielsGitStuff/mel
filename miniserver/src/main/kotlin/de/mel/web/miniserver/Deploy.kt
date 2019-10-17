package de.mel.web.miniserver

import de.mel.Lok
import de.mel.Versioner
import de.mel.auth.tools.N
import de.mel.auth.tools.lock.P
import de.mel.web.miniserver.http.BuildRequest
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

class Deploy(val miniServer: MiniServer, private val secretFile: File, val buildRequest: BuildRequest) {
    var props: Properties = Properties()
    fun run() {
        N.r { Lok.info("run()") }
        GlobalScope.launch {
            // we only want one instance to run at a time
            P.confine(miniServer).run {
                N.r { Lok.info("running") }
                props.load(secretFile.inputStream())
                fetch()
                val p = File(props.getProperty("projectRootDir")).absolutePath
                val projectRootDir = File(p)
                val miniServerDir = File(projectRootDir, "miniserver")
                val serverDir = File(miniServerDir, "server")//File("${projectRootDir}miniserver${File.separator}server")
                val secretDir = File(serverDir, "secret")
                val gradle = File(projectRootDir, "gradlew")
                val miniServerTarget = File(serverDir, "miniserver.jar")
                val serverFilesDir = File(serverDir, "files")

                Lok.info("working dir ${projectRootDir.absolutePath}")

                // pull
                Processor.runProcesses("pull from git", Processor("git", "pull", "-v"))
                // put keystore.properties in place
                val keyStoreFile = File(secretDir, "sign.jks")
                val keyStorePropFile = File(projectRootDir, "keystore.properties")
                val keyProps = Properties()
                keyProps["storePassword"] = miniServer.secretProperties["storePassword"]
                keyProps["keyPassword"] = miniServer.secretProperties["keyPassword"]
                keyProps["keyAlias"] = miniServer.secretProperties["keyAlias"]
                keyProps["storeFile"] = keyStoreFile.absolutePath
                keyProps.store(keyStorePropFile.outputStream(), "keep me private")
                //clean
                //gradle is senile from time to time and might mix up old compiled code with the recent one.
                val gradleCache = File(projectRootDir, ".gradle")
                if (gradleCache.exists())
                    gradleCache.deleteRecursively()
                Processor.runProcesses("clean",
                        Processor(gradle.absolutePath, "clean"),
                        Processor(gradle.absolutePath, ":app:clean"))

                // create a version and write it to the "version.now" file
                val version = kotlin.run {
                    val timestamp = LocalDateTime.ofEpochSecond(Date().time / 1000, 0, ZoneOffset.UTC)
                    val formatter = DateTimeFormatter.ofPattern(Versioner.VERSION_DATE_FORMAT_PATTERN)
                    timestamp.format(formatter)
                }
                with(version) {
                    val versionFile = File(projectRootDir, "build.tmp.version")
                    versionFile.writeText(version)
                }

                Lok.info("setting up deployed dir ${serverDir.absolutePath}")
                if (!buildRequest.keepBinaries!!) {
                    serverFilesDir.deleteRecursively()
                }
                serverFilesDir.mkdirs()

                //put update server certificate in place
                val updateCertFile = File(File(File(serverDir, "secret"), "socket"), "cert.cert")
                // holy shit
                val updateCertTarget = File(File(File(File(File(File(File(File(projectRootDir, "auth"), "src"), "main"), "resources"), "de"), "mel"), "auth"), "update.server.cert")
                Processor.runProcesses("copy update cert", Processor("cp", updateCertFile.absolutePath, updateCertTarget.absolutePath))

                //todo these tests need fixing: they should fail after a maximum time limit
                // tests take a shitload of time on that stupid machine
//                Processor.runProcesses("run tests",
//                        Processor(gradle.absolutePath, ":auth:test"),
//                        Processor(gradle.absolutePath, ":calendar:test"),
//                        Processor(gradle.absolutePath, ":contacts:test"),
//                        Processor(gradle.absolutePath, ":filesync:test"),
//                        Processor(gradle.absolutePath, ":filedump:test"),
//                        Processor(gradle.absolutePath, ":konsole:test"),
//                        Processor(gradle.absolutePath, ":miniserver:test"),
//                        Processor(gradle.absolutePath, ":serialize:test"),
//                        Processor(gradle.absolutePath, ":sql:test"))

                // assemble binaries
                val processList = mutableListOf<Processor>()


                if (buildRequest.jar!!)
                    processList.add(Processor(gradle.absolutePath, ":fxbundle:bootJar"))
                if (buildRequest.apk!!)
                    processList.add(Processor(gradle.absolutePath, ":app:assembleRelease"))
                if (buildRequest.server!!)
                    processList.add(Processor(gradle.absolutePath, ":miniserver:bootJar"))
                if (buildRequest.blog!!)
                    processList.add(Processor(gradle.absolutePath, ":blog:bootJar"))
                Processor.runProcesses("assemble/build", *processList.toTypedArray())


                //copy MiniServer.jar
                processList.clear()
                if (buildRequest.server!!) {
                    val miniServerSource = File("${projectRootDir.absolutePath}/miniserver/build/libs/").listFiles().filter { it.name.endsWith(".jar") }.first()
                    processList.add(Processor("cp", miniServerSource.absolutePath, miniServerTarget.absolutePath))
                }
                var apkFile: File? = null
                var fxFile: File? = null
                var blogFile: File? = null
                if (buildRequest.apk!!) {
                    processList.add(Processor("/bin/sh", "-c", "cp \"${projectRootDir.absolutePath}/app/build/outputs/apk/release/\"* \"${serverFilesDir.absolutePath}\""))
                    apkFile = File("${projectRootDir.absolutePath}/app/build/outputs/apk/release/").listFiles().filter { it.extension.toLowerCase() == "apk" }.first()
                }
                if (buildRequest.jar!!) {
                    processList.add(Processor("/bin/sh", "-c", "cp \"${projectRootDir.absolutePath}/fxbundle/build/libs/\"* \"${serverFilesDir.absolutePath}\""))
                    fxFile = File("${projectRootDir.absolutePath}/fxbundle/build/libs/").listFiles().filter { it.extension.toLowerCase() == "jar" }.first()
                }
                if (buildRequest.blog!!) {
                    processList.add(Processor("/bin/sh", "-c", "cp \"${projectRootDir.absolutePath}/blog/build/libs/\"* \"${serverFilesDir.absolutePath}\""))
                    blogFile = File("${projectRootDir.absolutePath}/blog/build/libs/").listFiles().filter { it.extension.toLowerCase() == "jar" }.first()
                }
                processList.add(Processor("rm", "-f", "${File(serverFilesDir, "output.json")}"))
                processList.add(Processor("/bin/sh", "-c", "chmod -R 700 \"$serverFilesDir\""))
                Processor.runProcesses("copying", *processList.toTypedArray())

                if (buildRequest.release!!) {
                    val relaseProcesses = mutableListOf<Processor>()
                    relaseProcesses.add(Processor("/bin/sh", "-c", "git tag -f -a \"$version\"  -m \"Released version $version\"\n"))
                    if (buildRequest.apk!!) {
                        relaseProcesses.add(Processor("/bin/sh", "-c", "cd ${projectRootDir.absolutePath}; ./release.sh $version \"${apkFile!!.absolutePath}\""))
                    }
                    if (buildRequest.jar!!) {
                        relaseProcesses.add(Processor("/bin/sh", "-c", "cd ${projectRootDir.absolutePath}; ./release.sh $version \"${fxFile!!.absolutePath}\""))
                    }
                    if (buildRequest.blog!!) {
                        relaseProcesses.add(Processor("/bin/sh", "-c", "cd ${projectRootDir.absolutePath}; ./release.sh $version \"${blogFile!!.absolutePath}\""))
                    }
                    Processor.runProcesses("upload to github", *relaseProcesses.toTypedArray())
                }

                // delete stop pipe
                miniServer.inputReader?.stop()
                // restart
                miniServer.reboot(serverDir, miniServerTarget)
            }.end()
        }
    }


    private fun fetch() {
        Lok.info("skipping fetch for now...")
    }
}

