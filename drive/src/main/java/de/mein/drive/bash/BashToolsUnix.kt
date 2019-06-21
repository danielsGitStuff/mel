package de.mein.drive.bash

import de.mein.Lok
import de.mein.auth.file.AFile
import de.mein.auth.tools.N
import de.mein.auth.file.DefaultFileConfiguration

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Executors

/**
 * Created by xor on 13.07.2017.
 */
open class BashToolsUnix : BashToolsImpl {


    override fun isSymLink(f: AFile<out AFile<*>>?): Boolean {
        if (f == null)
            return false
        return Files.isSymbolicLink(Paths.get(f.absolutePath))
    }

    protected var BIN_PATH = "/bin/sh"
    private val executorService = Executors.newCachedThreadPool()


    val inotifyLimit: Long?
        @Throws(IOException::class)
        get() {
            val f = File("/proc/sys/fs/inotify/max_user_watches")
            val lines = Files.readAllLines(Paths.get(f.toURI()))
            return java.lang.Long.parseLong(lines[0])
        }


    override fun setBinPath(binPath: String) {
        BIN_PATH = binPath
    }

    @Throws(IOException::class)
    override fun getINodesOfDirectory(file: AFile<*>): Set<Long> {
        val args = arrayOf(BIN_PATH, "-c", "find share/ -printf \"%p\\n\" | tail -n +2 | xargs -d '\\n' stat -c %i")
        val proc = ProcessBuilder(*args).start()
        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        val iNodes = HashSet<Long>()
        var line = reader.readLine()
        while (line != null) {
            iNodes.add(java.lang.Long.parseLong(line))
            line = reader.readLine()
        }
        return iNodes
    }

    /**
     * escapes this character: "
     *
     * @param file
     * @return
     */
    protected fun escapeQuotedAbsoluteFilePath(file: AFile<*>): String {
        return ("\"" + file.absolutePath
                .replace("\"".toRegex(), "\\\\\"")
                .replace("`".toRegex(), "\\\\`")
                .replace("\\$".toRegex(), "\\\\\\$")
                + "\"")
    }

    /**
     * escapes this character: "
     *
     * @param file
     * @return
     */
    protected fun escapeAbsoluteFilePath(file: AFile<*>): String {
        return file.absolutePath
                .replace("\"".toRegex(), "\\\\\"")
                .replace("`".toRegex(), "\\\\`")
                .replace("\\$".toRegex(), "\\\\\\$")
                .replace(" ".toRegex(), "\\ ")

    }

    @Throws(IOException::class, InterruptedException::class)
    override fun getFsBashDetails(file: AFile<*>): FsBashDetails {
        val args = arrayOf(BIN_PATH, "-c", "stat -c %i\\ %Y\\ '%F'\\ %N " + escapeQuotedAbsoluteFilePath(file))
        val proc = ProcessBuilder(*args).start()
        //proc.waitFor(); // this line sometimes hangs. Process.exitcode is 0 and Process.hasExited is false
        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        val line = reader.readLine()
        //todo debug
        if (line == null) {
            Lok.debug("reading error for: " + args[2])
            try {
                val r = BufferedReader(InputStreamReader(proc.errorStream))
                var l: String? = r.readLine()
                while (l != null) {
                    Lok.debug(l)
                    l = r.readLine()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val parts = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val iNode = java.lang.Long.parseLong(parts[0])
        val modified = java.lang.Long.parseLong(parts[1])
        val symLink = parts[2].startsWith("sym")
        val name = file.name
        var symLinkTarget: String? = null
        if (symLink) {
            symLinkTarget = parts[6]
            symLinkTarget = symLinkTarget.drop(1).dropLast(1)
        }
        return FsBashDetails(modified, iNode, symLink, symLinkTarget, name)
    }

    /**
     * parses an output line from stat
     */
    private fun parseStatLine(line: String): FsBashDetails {
        val parts = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val iNode = java.lang.Long.parseLong(parts[0])
        val modified = java.lang.Long.parseLong(parts[1])
        val symLink = parts[2].startsWith("sym")
        val name = "NAME"
        var symLinkTarget: String? = null
        if (symLink) {
            symLinkTarget = parts[6]
            symLinkTarget = symLinkTarget.drop(1).dropLast(1)
        }
        return FsBashDetails(modified, iNode, symLink, symLinkTarget, name)
    }

    override fun getContentFsBashDetails(directory: AFile<*>): MutableMap<String, FsBashDetails> {
        val map = mutableMapOf<String, FsBashDetails>()
        val path = "${escapeAbsoluteFilePath(directory)}${File.separator}*"
//        val args = arrayOf(BIN_PATH, "-c", "stat -c %i\\ %Y\\ '%F'\\ %N\\ %n " + path)
        val args = arrayOf(BIN_PATH, "-c", "stat -c %i\\ //\\ %Y\\ //\\ '%F'\\ //\\ %N\\ //\\ %n " + path)

        val proc = ProcessBuilder(*args).start()
        //proc.waitFor(); // this line sometimes hangs. Process.exitcode is 0 and Process.hasExited is false
        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        proc.waitFor()
        if (proc.exitValue() == 0) {
            reader.lines().forEach { line ->
                val parts = line.split("\\ //\\ ".toRegex()).toTypedArray()

//                val parts = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val iNode = java.lang.Long.parseLong(parts[0])
                val modified = java.lang.Long.parseLong(parts[1])
                val symLink = parts[2].startsWith("sym")
                val absolutePath = parts[4]
                val name = File(absolutePath).name
                var symLinkTarget: String? = null
                if (symLink) {
                    // parse something like: "'/a/b '-> '/c/d'"
                    // drop quotes, spaces etc
                    symLinkTarget = parts[3].drop(absolutePath.length + 7).dropLast(1)
                }
                val details = FsBashDetails(modified, iNode, symLink, symLinkTarget, name)
                map[details.name] = details
            }
        } else {
            args.forEach {
                Lok.error("arg: $it")
            }
            val r = BufferedReader(InputStreamReader(proc.errorStream))
            r.lines().forEach {
                Lok.error(it)
            }
        }
        return map
    }

    /**
     * rm -rf
     *
     * @param directory
     */
    @Throws(IOException::class)
    override fun rmRf(directory: AFile<*>) {
        val args = arrayOf(BIN_PATH, "-c", "rm -rf " + escapeQuotedAbsoluteFilePath(directory))
        val proc = ProcessBuilder(*args).start()
        N.oneLine { proc.waitFor() }
    }

    @Throws(IOException::class, BashToolsException::class)
    override fun stuffModifiedAfter(referenceFile: AFile<*>, directory: AFile<*>, pruneDir: AFile<*>): List<AFile<*>> {
        Lok.debug("BashTools.stuffModifiedAfter: " + referenceFile.name + " mod: " + referenceFile.lastModified())
        //        String cmd = "find \"" + directory.getAbsolutePath() + "\"  "
        //                + " -path \"" + pruneDir + "\" -prune"
        //                + " -o -newer \"" + referenceFile.getAbsolutePath() + "\" -print";
        val cmd = ("find " + escapeQuotedAbsoluteFilePath(directory)
                + " -path " + escapeQuotedAbsoluteFilePath(pruneDir) + " -prune"
                + " -o -newer " + escapeQuotedAbsoluteFilePath(referenceFile) + " -print")
        Lok.debug("BashTools.stuffModifiedAfter.cmd: $cmd")
        val args = arrayOf(BIN_PATH, "-c", cmd)
        val processBuilder = ProcessBuilder(*args)
        processBuilder.redirectErrorStream(true)
        val proc = processBuilder.start()
        Lok.debug("BashTools.stuffModifiedAfter.collecting.result")
        val result = ArrayList<AFile<*>>()
        val iterator = BashTools.inputStreamToFileIterator(proc.inputStream)
        while (iterator.hasNext()) {
            val path = iterator.next()
            Lok.debug(javaClass.simpleName + ".stuffModifiedAfter.collected: " + path)
            result.add(path)
        }
        Lok.debug("BashTools.stuffModifiedAfter.collecting.done")
        return result
    }

    @Throws(IOException::class)
    private fun exec(cmd: String): Iterator<AFile<*>> {
        val args = arrayOf(BIN_PATH, "-c", cmd)
        Lok.debug("BashToolsUnix.exec: $cmd")
        val proc = ProcessBuilder(*args).start()
        return BashTools.inputStreamToFileIterator(proc.inputStream)
    }

    @Throws(IOException::class)
    override fun find(directory: AFile<*>, pruneDir: AFile<*>): Iterator<AFile<*>> {
        return exec("find " + escapeQuotedAbsoluteFilePath(directory) + " -mindepth 1" + " -path " + escapeQuotedAbsoluteFilePath(pruneDir) + " -prune -o -print")
    }

    override fun stuffModifiedAfter(directory: AFile<*>, pruneDir: AFile<*>, timeStamp: Long): Iterator<AFile<*>>? {
        System.err.println("BashToolsUnix.stuffModifiedAfter()... I AM THE UNIX GUY! >:(")
        return null
    }

    @Throws(IOException::class)
    override fun mkdir(dir: AFile<*>) {
        val args = arrayOf(BIN_PATH, "-c", "mkdir " + escapeQuotedAbsoluteFilePath(dir))
        ProcessBuilder(*args).start()
    }

    @Throws(IOException::class)
    override fun mv(source: File, target: File): Boolean {
        val src = source.absolutePath.replace("'".toRegex(), "\\'")
        val tgt = target.absolutePath.replace("'".toRegex(), "\\'")
        val cmd = "mv '$src' '$tgt'"
        val args = arrayOf(BIN_PATH, "-c", cmd)
        val process = ProcessBuilder(*args).start()
        try {
            process.waitFor()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        val reader = BufferedReader(InputStreamReader(process.errorStream))
        val error = reader.readLine() ?: return true
        Lok.debug("BashToolsUnix.mv")
        return false
    }

    class ShareFolderProperties(val counted: Long, val containsSymLinks: Boolean)

    @Throws(IOException::class, InterruptedException::class)
    fun getShareFolderPropeties(dir: File): ShareFolderProperties {
        var count: Long = 0L
        var containsSymLinks = false;
        val thread = Thread(Runnable {
            dir.walkTopDown().onEnter { file ->
                if (Files.isSymbolicLink(Paths.get(file.toURI()))) {
                    containsSymLinks = true
                    false
                } else
                    file.isDirectory
            }.forEach { count++ }
        })
        thread.start()
        thread.join()
        return ShareFolderProperties(count, containsSymLinks)
    }

    companion object {

        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            AFile.configure(DefaultFileConfiguration())
            val f = AFile.instance("f")
            f.mkdirs()
            val bashToolsUnix = BashToolsUnix()
            val modifiedAndInode = bashToolsUnix.getFsBashDetails(f)
            Lok.debug("mod " + modifiedAndInode.modified + " ... inode " + modifiedAndInode.getiNode())
            f.delete()
        }
    }
}
