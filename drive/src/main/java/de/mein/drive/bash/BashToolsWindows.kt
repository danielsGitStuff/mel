package de.mein.drive.bash

import de.mein.Lok
import de.mein.Processor
import de.mein.auth.file.AFile

import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

/**
 * Created by xor on 13.07.2017.
 */
class BashToolsWindows : BashToolsImpl() {
    override fun getContentFsBashDetails(file: AFile<*>): Map<String, FsBashDetails> {
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        System.exit(-1)
        return mutableMapOf()
    }

    override fun lnS(file: AFile<out AFile<*>>, targetString: String) {
        // we are limited to two ways of using 'mklink' here.
        // /J works for directories whereas /H works for linking files
        val target = File(targetString)
        val param = if (target.isDirectory) "/J" else "/H"
        exec("mklink", param, file.absolutePath, targetString).waitFor()
    }



    override fun isSymLink(f: AFile<out AFile<*>>): Boolean {
        if (Files.isSymbolicLink(Paths.get(File(f.absolutePath).toURI())))
            return true
        val path = f.absolutePath
        if (f.isDirectory) {
            // check if junction
            var countDown = 5;
            execReader("dir", "/al", f.parentFile.absolutePath)?.useLines {
                it.iterator().forEach {
                    if (countDown == 0) {
                        var stripped = it.substringAfter("<JUNCTION>").trim()
                        if (stripped.startsWith(f.name)) {
                            return true
//                            stripped = stripped.substringAfter(f.name).trim()
//                            stripped = stripped.substring(1, stripped.length - 1)
//                            if (path == stripped)
//                                return true
                        }
                    }
                    countDown--
                }
            }
            return false
        } else {
            // check if hardlink

            // first get drive letter, we need to add that later
            val driveLetter = f.absolutePath.substring(0,2)
            execReader("fsutil", "hardlink", "list", f.absolutePath)?.useLines {
                it.iterator().forEach {
                    val readPath = driveLetter + it
                    if (path != readPath)
                        return true
                }
            }
        }
        return false
    }

    override fun setBinPath(binPath: String) {
        Lok.debug("BashToolsWindows.setBinPath")
    }

    @Throws(IOException::class)
    override fun getINodesOfDirectory(file: AFile<*>): Set<Long>? {
        val processor = Processor("")
        return null
    }

    @Throws(IOException::class)
    override fun getFsBashDetails(file: AFile<*>): FsBashDetails {
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")
        Lok.error("NOT:COMPLETELY:IMPLEMENTED")


        //reads something like "File ID is 0x0000000000000000000200000000063a"
        val result = execLine("fsutil", "file", "queryfileid", file.absolutePath)
        val id = result!!.substring(11)
        val iNode = java.lang.Long.decode(id)
        val symLinkTarget = "not implemented yet"
        val name = "NAME"
        return FsBashDetails(file.lastModified(), iNode, false, symLinkTarget, name)
    }

    @Throws(IOException::class)
    override fun rmRf(directory: AFile<*>) {
        //        exec("rd /s /q \"" + directory.getAbsolutePath() + "\"");
        exec("rd", "/s", "/q", directory.absolutePath)
    }

    @Throws(IOException::class, BashToolsException::class)
    override fun stuffModifiedAfter(referenceFile: AFile<*>, directory: AFile<*>, pruneDir: AFile<*>): List<AFile<*>> {
        System.err.println("BashToolsWindows.stuffModifiedAfter.I AM THE WINDOWS GUY!")
        return listOf()
    }

    private fun buildArgs(vararg commands: String): Array<String?> {
        val result = arrayOfNulls<String>(commands.size + 2)
        result[0] = BIN_PATH
        result[1] = "/c"
        var i = 2
        for (command in commands) {
            result[i] = command
            i++
        }
        return result
    }

    @Throws(IOException::class)
    private fun exec(vararg commands: String): Process {
        Lok.debug("BashToolsWindows.exec: " + Arrays.toString(commands));
        val args = buildArgs(*commands)
        return ProcessBuilder(*args).start()
    }

    @Throws(IOException::class)
    private fun execLine(vararg commands: String): String? {
        try {
            val process = exec(*commands)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            return reader.readLine()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    @Throws(IOException::class)
    private fun execReader(vararg commands: String): WindowsBashReader? {
        try {
            val process = exec(*commands)
            process.waitFor()
            return WindowsBashReader(InputStreamReader(process.inputStream, CHARSET))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    @Throws(IOException::class)
    override fun find(directory: AFile<*>, pruneDir: AFile<*>): AutoKlausIterator<AFile<*>> {
        return object : AutoKlausIterator<AFile<*>> {
            val windowsBashReader = execReader("dir", "/b/s", directory.absolutePath, "|", "findstr", "/vc:\"" + pruneDir.absolutePath + "\"")
            val iterator = windowsBashReader!!.lines()
                    .map { it: String -> AFile.instance(it) }.iterator()

            override fun hasNext(): Boolean = iterator.hasNext()

            override fun next(): AFile<*> = iterator.next()

            override fun remove() {

            }

            override fun close() {
                windowsBashReader!!.close()
            }

        }

        //.map<AFile>(Function<String, AFile> { it:String -> AFile.instance(it) }).iterator()
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun execPowerShell(command: String, prependLine: String?): WindowsPowerReader {
        Lok.debug("BashToolsWindows.execPowerShell: $command")
        val args = arrayOf("powershell.exe")
        val process = ProcessBuilder(*args).start()
        val stdin = PrintWriter(process.outputStream)
        stdin.println(command)
        stdin.close()
        val reader = WindowsPowerReader(InputStreamReader(process.inputStream))
        reader.prependLine(prependLine)
        //process.waitFor();
        return reader
//        return reader.lines().map(Function<String, AFile<*>> { s: String -> AFile.instance(s) })
    }

    @Throws(IOException::class, InterruptedException::class)
    override fun stuffModifiedAfter(directory: AFile<*>, pruneDir: AFile<*>, timeStamp: Long): AutoKlausIterator<AFile<*>> {
        val winTimeStamp = timeStamp / 1000.0
        var prependLine: String? = null
        val lm = directory.lastModified()
        if (directory.lastModified() >= timeStamp) {
            prependLine = directory.absolutePath
        }
        val command = "get-childitem \"" + directory.absolutePath + "\" -recurse | " +
                "where {(Get-Date(\$_.LastWriteTime.ToUniversalTime()) -UFormat \"%s\") -gt " + winTimeStamp + " -and -not \$_.FullName.StartsWith(\"" + pruneDir.absolutePath + "\")} " +
                "| foreach {\$_.FullName}"
        return object : AutoKlausIterator<AFile<*>> {
            val windowsBashReader = execPowerShell(command, prependLine)
            val iterator = windowsBashReader.lines().map { AFile.instance(it) }.iterator()

            override fun hasNext(): Boolean = iterator.hasNext()


            override fun next(): AFile<*> = iterator.next()

            override fun remove() {

            }

            override fun close() {
                windowsBashReader.close()
            }

        }
    }

    @Throws(IOException::class)
    override fun mkdir(dir: AFile<*>) {
        exec("mkdir", dir.absolutePath)
    }

    @Throws(IOException::class)
    override fun mv(source: File, target: File): Boolean {
        System.err.println("BashToolsWindows.mv.NOT:IMPLEMENTED")
        return false
    }

    companion object {
        private val BIN_PATH = "cmd"
        private val CHARSET = StandardCharsets.ISO_8859_1
    }
}
