package de.mel.filesync.bash

import de.mel.Lok
import de.mel.auth.file.AbstractFile
import de.mel.auth.file.StandardFile
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
class BashToolsWindows : BashTools<StandardFile>() {
    override fun getContentFsBashDetails(file: StandardFile): MutableMap<String, FsBashDetails> {
        val contents: Array<out StandardFile>? = file.listContent()

        val symLinkMap: MutableMap<String, String> = mutableMapOf()
        val iNodeMap: MutableMap<String, Long> = mutableMapOf()

        runBlocking(Dispatchers.IO) {

            // get inodes
            contents?.forEach {
                launch {
                    val fsUtil = execLine("fsutil", "file", "queryfileid", file.absolutePath)
                    val id = fsUtil!!.substringAfter(": ")
                    val iNode = java.lang.Long.decode(id)
                    iNodeMap[it.getName()] = iNode
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
                                symLinkMap[f.getName()] = readPath
                        }
                    }
                }
            }

        }
        val map: MutableMap<String, FsBashDetails> = mutableMapOf()
        contents?.forEach {
            val name = it.getName()
            val isSymLink = symLinkMap.containsKey(name)
            val attr = Files.getFileAttributeView(Paths.get(File(it.absolutePath).toURI()), BasicFileAttributeView::class.java).readAttributes()
            val details = FsBashDetails(attr.creationTime().toMillis(), attr.lastModifiedTime().toMillis(), iNodeMap[name], isSymLink, symLinkMap[name], name)
            map[name] = details
        }
        return map
    }

    @Throws(IOException::class)
    override fun getFsBashDetails(file: StandardFile): FsBashDetails {
        var iNode: Long? = null
        val name = file.getName()
        var isSymLink: Boolean = false
        var symLinkTarget: String? = null
        var created: Long? = null
        var modified: Long? = null

        runBlocking {

            launch {
                //reads something like "File ID: 0x0000000000000000000200000000063a"
                val fsUtil = execLine("fsutil", "file", "queryfileid", file.absolutePath)
                val id = fsUtil!!.substringAfter(": ")
                iNode = java.lang.Long.decode(id)
            }

            launch {
                symLinkTarget = getSymLink(file)
                if (symLinkTarget != null)
                    isSymLink = true
            }
            launch {
                val attr = Files.getFileAttributeView(Paths.get(File(file.absolutePath).toURI()), BasicFileAttributeView::class.java).readAttributes()
                created = attr.creationTime().toMillis()
                modified = attr.lastModifiedTime().toMillis()
            }
        }
        return FsBashDetails(created, modified, iNode!!, isSymLink, symLinkTarget, name)
    }

    override fun lnS(file: StandardFile, targetString: String) {
        // we are limited to two ways of using 'mklink' here.
        // /J works for directories whereas /H works for linking files
        val target = File(targetString)
        val param = if (target.isDirectory) "/J" else "/H"
        exec("mklink", param, file.absolutePath, targetString).waitFor()
    }


    override fun isSymLink(f: StandardFile): Boolean {
        if (Files.isSymbolicLink(Paths.get(File(f.absolutePath).toURI())))
            return true
        val path = f.absolutePath
        if (f.isDirectory) {
            // check if junction
            var countDown = 5;
            execReader("dir", "/al", f.getParentFile()!!.absolutePath)?.useLines {
                it.iterator().forEach {
                    if (countDown == 0) {
                        var stripped = it.substringAfter("<JUNCTION>").substring(5)
                        if (stripped.startsWith(f.getName())) {
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

    private fun getSymLink(f: StandardFile): String? {
        if (Files.isSymbolicLink(Paths.get(File(f.absolutePath).toURI())))
            return Files.readSymbolicLink(Paths.get(File(f.absolutePath).toURI())).toFile().absolutePath
        val path = f.absolutePath
        if (f.isDirectory) {
            // check if junction
            var countDown = 5;
            execReader("dir", "/al", f.getParentFile()!!.absolutePath)?.useLines {
                it.iterator().forEach {
                    if (countDown == 0) {
                        var stripped = it.substringAfter("<JUNCTION>").substring(5)
                        if (stripped.startsWith(f.getName())) {
                            stripped = stripped.substringAfter(f.getName()).trim()
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
    override fun rmRf(directory: StandardFile) {
        //        exec("rd /s /q \"" + directory.getAbsolutePath() + "\"");
        exec("rd", "/s", "/q", directory.absolutePath).waitFor()
//        Files.delete(Paths.get(File(directory.absolutePath).toURI()))

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
//        Lok.debug("BashToolsWindows.exec: " + Arrays.toString(commands));
        val args = buildArgs(*commands)
        val process = ProcessBuilder(*args).start()
        return process
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
//            process.waitFor()
            return WindowsBashReader(InputStreamReader(process.inputStream, CHARSET))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    @Throws(IOException::class)
    override fun find(directory: StandardFile, pruneDir: StandardFile): AutoKlausIterator<StandardFile> {
        return object : AutoKlausIterator<StandardFile> {
            val windowsBashReader = execReader("dir", "/b/s", directory.absolutePath, "|", "findstr", "/vc:\"" + pruneDir.absolutePath + "\"")!!
                    .addFirstLine(directory.absolutePath)
            val iterator = windowsBashReader!!.lines()
                    .map { it: String -> AbstractFile.instance(it) as StandardFile}.iterator()

            override fun hasNext(): Boolean = iterator.hasNext()

            override fun next(): StandardFile = iterator.next()

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
    override fun stuffModifiedAfter(directory: StandardFile, pruneDir: StandardFile, timeStamp: Long): AutoKlausIterator<StandardFile> {
        val winTimeStamp = timeStamp / 1000.0
        var prependLine: String? = null
        val lm = directory.lastModified()
        // todo null
        if (directory.lastModified()!! >= timeStamp) {
            prependLine = directory.absolutePath
        }
        val command = "get-childitem \"" + directory.absolutePath + "\" -recurse | " +
                "where {(Get-Date(\$_.LastWriteTime.ToUniversalTime()) -UFormat \"%s\") -gt " + winTimeStamp + " -and -not \$_.FullName.StartsWith(\"" + pruneDir.absolutePath + "\")} " +
                "| foreach {\$_.FullName}"
        return object : AutoKlausIterator<StandardFile> {
            val windowsBashReader = execPowerShell(command, prependLine)
            val iterator = windowsBashReader.lines().map { AbstractFile.instance(it) as StandardFile }.iterator()

            override fun hasNext(): Boolean = iterator.hasNext()


            override fun next(): StandardFile = iterator.next()


            override fun close() {
                windowsBashReader.close()
            }

        }
    }

    companion object {
        private val BIN_PATH = "cmd"
        private val CHARSET = StandardCharsets.ISO_8859_1
    }

    override fun stuffModifiedAfter(referenceFile: StandardFile, directory: StandardFile, pruneDir: StandardFile): List<StandardFile> = mutableListOf()
}
