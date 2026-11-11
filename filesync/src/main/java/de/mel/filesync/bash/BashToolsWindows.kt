package de.mel.filesync.bash

import de.mel.Lok
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributeView


/**
 * Symlinks are realised using hardlinks for files and junctions for directories.
 * Reason: Windoof permits creating symlinks without higher privileges in out of the box configuration
 * Created by xor on 13.07.2017.
 */
class BashToolsWindows : BashTools() {
    override fun getContentFsBashDetails(file: File): MutableMap<String, FsBashDetails> {
        val contents: Array<out File>? = file.listFiles()

        val symLinkMap: MutableMap<String, String> = mutableMapOf()
        val iNodeMap: MutableMap<String, Long> = mutableMapOf()

        runBlocking(Dispatchers.IO) {

            // get inodes
            contents?.forEach {
                launch {
                    val fsUtil = execLine("fsutil", "file", "queryfileid", file.absolutePath)
                    val end = fsUtil!!.length
                    val start = end - 34;
                    val id = fsUtil.substring(start, end)
                    val iNode = java.lang.Long.decode(id)
                    iNodeMap[it.name] = iNode
                }
            }

            // get junctions
            launch {
                var countDown = 5;
                execReader("dir", "/al", file.absolutePath)?.useLines {
                    it.iterator().forEach {
                        if (countDown == 0) {
                            // parse something like this: 17.08.2019  01:56    <JUNCTION>     bla bla [C:\Users\user\testdir.root\sub]
                            val stripped = it.substringAfter("<JUNCTION>").substring(5)
                            // extract name by using the colon after drive letter
                            val name = stripped.substring(0, stripped.indexOf(':') - 3)
                            // extract target by subtracting name
                            val target = stripped.substring(name.length + 2, stripped.length - 1)
                            symLinkMap[name] = target
                        }
                        countDown--
                    }
                }
            }

            // get hard links
            val driveLetter = file.absolutePath.substring(0, 2)
            contents?.filter { !it.isDirectory }?.forEach { f ->
                launch {
                    val path = f.absolutePath
                    execReader("fsutil", "hardlink", "list", f.absolutePath)?.useLines {
                        it.iterator().forEach {
                            val readPath = driveLetter + it
                            if (path != readPath)
                                symLinkMap[f.name] = readPath
                        }
                    }
                }
            }

        }
        val map: MutableMap<String, FsBashDetails> = mutableMapOf()
        contents?.forEach {
            val name = it.name
            val isSymLink = symLinkMap.containsKey(name)
            val attr = Files.getFileAttributeView(Paths.get(it.toURI()), BasicFileAttributeView::class.java).readAttributes()
            val details = FsBashDetails(attr.creationTime().toMillis(), attr.lastModifiedTime().toMillis(), iNodeMap[name], isSymLink, symLinkMap[name], name)
            map[name] = details
        }
        return map
    }

    @Throws(IOException::class)
    override fun getFsBashDetails(file: File): FsBashDetails {
        var iNode: Long? = null
        val name = file.name
        var isSymLink: Boolean = false
        var symLinkTarget: String? = null
        var created: Long? = null
        var modified: Long? = null

        runBlocking {

            launch {
                //reads something like "File ID: 0x0000000000000000000200000000063a"
                val fsUtil = execLine("fsutil", "file", "queryfileid", file.absolutePath)
                val end = fsUtil!!.length
                val start = end - 34;
                val id = fsUtil.substring(start, end)
                iNode = java.lang.Long.decode(id)
            }

            launch {
                symLinkTarget = getSymLink(file)
                if (symLinkTarget != null)
                    isSymLink = true
            }
            launch {
                val attr = Files.getFileAttributeView(Paths.get(file.toURI()), BasicFileAttributeView::class.java).readAttributes()
                created = attr.creationTime().toMillis()
                modified = attr.lastModifiedTime().toMillis()
            }
        }
        return FsBashDetails(created, modified, iNode!!, isSymLink, symLinkTarget, name)
    }

    override fun lnS(file: File, targetString: String) {
        // we are limited to two ways of using 'mklink' here.
        // /J works for directories whereas /H works for linking files
        val target = File(targetString)
        val param = if (target.isDirectory) "/J" else "/H"
        exec("mklink", param, file.absolutePath, targetString).waitFor()
    }

    override fun isSymLink(f: File): Boolean {
        if (Files.isSymbolicLink(Paths.get(f.toURI())))
            return true
        val path = f.absolutePath
        if (f.isDirectory) {
            // check if junction
            var countDown = 5;
            execReader("dir", "/al", f.parentFile!!.absolutePath)?.useLines {
                it.iterator().forEach {
                    if (countDown == 0) {
                        var stripped = it.substringAfter("<JUNCTION>").substring(5)
                        if (stripped.startsWith(f.name)) {
                            return true
                        }
                    }
                    countDown--
                }
            }
            return false
        } else {
            // check if hardlink
            // first get drive letter, we need to add that later
            val driveLetter = f.absolutePath.substring(0, 2)
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

    private fun getSymLink(f: File): String? {
        if (Files.isSymbolicLink(Paths.get(f.toURI())))
            return Files.readSymbolicLink(Paths.get(f.toURI())).toFile().absolutePath
        val path = f.absolutePath
        if (f.isDirectory) {
            // check if junction
            var countDown = 5;
            execReader("dir", "/al", f.parentFile!!.absolutePath)?.useLines {
                it.iterator().forEach {
                    if (countDown == 0) {
                        var stripped = it.substringAfter("<JUNCTION>").substring(5)
                        if (stripped.startsWith(f.name)) {
                            stripped = stripped.substringAfter(f.name).trim()
                            stripped = stripped.substring(1, stripped.length - 1)
                            if (path != stripped)
                                return stripped
                        }
                    }
                    countDown--
                }
            }
            return null
        } else {
            // check if hardlink

            // first get drive letter, we need to add that later
            val driveLetter = f.absolutePath.substring(0, 2)
            execReader("fsutil", "hardlink", "list", f.absolutePath)?.useLines {
                it.iterator().forEach {
                    val readPath = driveLetter + it
                    if (path != readPath)
                        return readPath
                }
            }
        }
        return null
    }

    @Throws(IOException::class)
    override fun rmRf(directory: File) {
        exec("rd", "/s", "/q", directory.absolutePath).waitFor()
    }

    private fun exec(vararg cmd: String): Process {
        val wholeCmd = arrayOf(BIN_PATH, "/c") + cmd
        Lok.debug("BashToolsWindows.exec: " + wholeCmd.joinToString(" "))
        val process = ProcessBuilder(*wholeCmd).start()
        return process
    }

    private fun execLine(vararg cmd: String): String? {
        val reader = execReader(*cmd)
        val line = reader?.readLine()
        reader?.close()
        return line
    }

    private fun execReader(vararg cmd: String): WindowsPowerReader? {
        val wholeCmd = arrayOf(BIN_PATH, "/c") + cmd
        val process = ProcessBuilder(*wholeCmd).start()
        val reader = WindowsPowerReader(InputStreamReader(process.inputStream, CHARSET))
        return reader
    }

    class WindowsPowerReader(ins: Reader) : BufferedReader(ins) {
        private var prependLine: String? = null

        fun addFirstLine(line: String): WindowsPowerReader {
            this.prependLine = line
            return this
        }

        fun prependLine(prependLine: String?) {
            this.prependLine = prependLine
        }

        override fun readLine(): String? {
            if (prependLine != null) {
                val line = prependLine
                prependLine = null
                return line
            } else
                return super.readLine()
        }
    }

    @Throws(IOException::class)
    override fun find(directory: File, pruneDir: File): AutoKlausIterator<File> {
        return object : AutoKlausIterator<File> {
            val windowsBashReader = execReader("dir", "/b/s", directory.absolutePath, "|", "findstr", "/vc:\"" + pruneDir.absolutePath + "\"")!!
                    .addFirstLine(directory.absolutePath)
            val iterator = windowsBashReader!!.lines()
                    .map { File(it) }.iterator()

            override fun hasNext(): Boolean = iterator.hasNext()

            override fun next(): File = iterator.next()

            override fun close() {
                windowsBashReader!!.close()
            }

        }
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
        return reader
    }

    @Throws(IOException::class, InterruptedException::class)
    override fun stuffModifiedAfter(directory: File, pruneDir: File, timeStamp: Long): AutoKlausIterator<File> {
        val winTimeStamp = timeStamp / 1000.0
        var prependLine: String? = null
        if (directory.lastModified() >= timeStamp) {
            prependLine = directory.absolutePath
        }
        val command = "get-childitem \"" + directory.absolutePath + "\" -recurse | " +
                "where {(Get-Date(\$_.LastWriteTime.ToUniversalTime()) -UFormat \"%s\") -gt " + winTimeStamp + " -and -not \$_.FullName.StartsWith(\"" + pruneDir.absolutePath + "\")} " +
                "| foreach {\$_.FullName}"
        return object : AutoKlausIterator<File> {
            val windowsBashReader = execPowerShell(command, prependLine)
            val iterator = windowsBashReader.lines().map { File(it) }.iterator()

            override fun hasNext(): Boolean = iterator.hasNext()

            override fun next(): File = iterator.next()

            override fun close() {
                windowsBashReader.close()
            }

        }
    }

    companion object {
        private val BIN_PATH = "cmd"
        private val CHARSET = StandardCharsets.ISO_8859_1
    }

    override fun stuffModifiedAfter(referenceFile: File, directory: File, pruneDir: File): List<File> = mutableListOf()
}
