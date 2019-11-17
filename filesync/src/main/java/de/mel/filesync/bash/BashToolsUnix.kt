package de.mel.filesync.bash

import de.mel.Lok
import de.mel.auth.file.AbstractFile
import de.mel.auth.file.IFile
import de.mel.auth.file.StandardFile
import de.mel.auth.tools.N

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
open class BashToolsUnix : BashTools<StandardFile>() {
    override fun lnS(file: StandardFile, target: String) {
        val escapedFilePath = escapeQuotedAbsoluteFilePath(file)
        val escapedTarget = escapeQuotedPath(target)
        val args = arrayOf(BIN_PATH, "-c", "ln -s ${escapedTarget} ${escapedFilePath} ")
        val proc = ProcessBuilder(*args).start()
        proc.waitFor()
    }

    // use this command to return the result of the actual command in English.
    private val unfrench = "LC_ALL='C' "
    protected var readCreated: Boolean = true
    // todo use BashTools.binPath
    protected var BIN_PATH = "bash"
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

    /**
     * escapes this character: "
     *
     * @param file
     * @return
     */
    protected fun escapeQuotedAbsoluteFilePath(file: StandardFile): String {
        return ("\"${escapeAbsoluteFilePath(file)}\"")
    }

    /**
     * escapes this character: "
     *
     * @param file
     * @return
     */
    protected fun escapeAbsoluteFilePath(file: StandardFile): String {
        return escapePath(file.absolutePath)

    }

    protected fun escapeQuotedPath(path: String): String {
        return "\"${escapePath(path)}\""
    }

    protected fun escapePath(path: String): String {
        return path.replace("\"".toRegex(), "\\\\\"")
                .replace("`".toRegex(), "\\\\`")
                .replace("\\$".toRegex(), "\\\\\\$")
                .replace(" ".toRegex(), "\\ ")
    }


    // todo null
    @Throws(IOException::class, InterruptedException::class)
    override fun getFsBashDetails(file: StandardFile): FsBashDetails? {
        val args = arrayOf(BIN_PATH, "-c", "$unfrench stat -c %i\\ %W\\ '%F'\\ %N " + escapeQuotedAbsoluteFilePath(file))
        val proc = ProcessBuilder(*args).start()
        //proc.waitFor(); // this line sometimes hangs. Process.exitcode is 0 and Process.hasExited is false
        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        val line = reader.readLine()
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
        val created = if (readCreated) java.lang.Long.parseLong(parts[1]) * 1000 else null
        val symLink = parts[2].startsWith("sym")
        val name = file.name
        val modified = file.lastModified()
        val symLinkTarget: String? = if (symLink)
            parseSymLink(file, parts[6])
        else
            null
        return createFsBashDetails(created, modified!!, iNode, symLink, symLinkTarget, name)
    }

    /**
     * parses an output line from stat
     */
    private fun parseSymLink(file: StandardFile, line: String): String {
        var symLinkTarget: String? = null
        val originalSym = line.drop(1).dropLast(1)
        val parentCanonical = file.parentFile.canonicalPath
        val completePath = parentCanonical + File.separator + originalSym
        val ComplecteCanonical = File(completePath).canonicalPath
        symLinkTarget = ComplecteCanonical.drop(parentCanonical.length)

        if (symLinkTarget == "")
            symLinkTarget = "."
        if (symLinkTarget.startsWith(File.separator))
            symLinkTarget = symLinkTarget.drop(1)
        return symLinkTarget
    }

    override fun getContentFsBashDetails(directory: StandardFile): MutableMap<String, FsBashDetails> {
        val map = mutableMapOf<String, FsBashDetails>()
        val escaped = escapeAbsoluteFilePath(directory)
        // the secont path below fixed something but I forgot what it is. it also returns "." and ".."
        val path = "\"$escaped${File.separator}\"* \"$escaped${File.separator}\".*"
        val args = arrayOf(BIN_PATH, "-c", "$unfrench stat -c %i\\ //\\ %W\\ //\\ '%F'\\ //\\ %N\\ //\\ %n $path;")

        val proc = ProcessBuilder(*args).start()
//        proc.waitFor(); // this line sometimes hangs. Process.exitcode is 0 and Process.hasExited is false
        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        reader.lines().forEach { line ->
            val parts = line.split("\\ //\\ ".toRegex()).toTypedArray()

//                val parts = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val iNode = java.lang.Long.parseLong(parts[0])
            val created = if (readCreated) java.lang.Long.parseLong(parts[1]) * 1000 else null
            val symLink = parts[2].startsWith("sym")
            val absolutePath = parts[4]
            val f = File(absolutePath)
            val name = f.name
            val modified = f.lastModified()
            var symLinkTarget: String? = null
            if (symLink) {
                // parse something like: "'/a/b '-> '/c/d'"
                // drop quotes, spaces etc
                symLinkTarget = parts[3].drop(absolutePath.length + 7).dropLast(1)
            }
            val details = createFsBashDetails(created, modified, iNode, symLink, symLinkTarget, name) //FsBashDetails(created, modified, iNode, symLink, symLinkTarget, name)
            map[details.name] = details
        }
        return map
    }

    /**
     * rm -rf
     *
     * @param directory
     */
    @Throws(IOException::class)
    override fun rmRf(directory: StandardFile) {
        val args = arrayOf(BIN_PATH, "-c", "rm -rf " + escapeQuotedAbsoluteFilePath(directory))
        val proc = ProcessBuilder(*args).start()
        N.oneLine { proc.waitFor() }
    }

    @Throws(IOException::class, BashToolsException::class)
    override fun stuffModifiedAfter(referenceFile: StandardFile, directory: StandardFile, pruneDir: StandardFile): List<StandardFile> {
        Lok.debug("BashTools.Companion.stuffModifiedAfter: " + referenceFile.name + " mod: " + referenceFile.lastModified())
        //        String cmd = "find \"" + directory.getAbsolutePath() + "\"  "
        //                + " -path \"" + pruneDir + "\" -prune"
        //                + " -o -newer \"" + referenceFile.getAbsolutePath() + "\" -print";
        val cmd = ("find " + escapeQuotedAbsoluteFilePath(directory)
                + " -path " + escapeQuotedAbsoluteFilePath(pruneDir) + " -prune"
                + " -o -newercc " + escapeQuotedAbsoluteFilePath(referenceFile) + " -print")
        Lok.debug("BashTools.Companion.stuffModifiedAfter.cmd: $cmd")
        val args = arrayOf(BIN_PATH, "-c", cmd)
        val processBuilder = ProcessBuilder(*args)
        processBuilder.redirectErrorStream(true)
        val proc = processBuilder.start()
        Lok.debug("BashTools.Companion.stuffModifiedAfter.collecting.result")
        val result = ArrayList<StandardFile>()
        val iterator = BufferedIterator.BufferedFileIterator<StandardFile>(InputStreamReader(proc.inputStream))
        while (iterator.hasNext()) {
            val path = iterator.next()
//            Lok.debug(javaClass.simpleName + ".stuffModifiedAfter.collected: " + path)
            result.add(path)
        }
        Lok.debug("BashTools.Companion.stuffModifiedAfter.collecting.done")
        return result
    }

    @Throws(IOException::class)
    private fun exec(cmd: String): AutoKlausIterator<StandardFile> {
        val args = arrayOf(BIN_PATH, "-c", cmd)
        Lok.debug("BashToolsUnix.exec: $cmd")
        val proc = ProcessBuilder(*args).start()
        return BufferedIterator.BufferedFileIterator(InputStreamReader(proc.inputStream))
    }

    @Throws(IOException::class)
    override fun find(directory: StandardFile, pruneDir: StandardFile): AutoKlausIterator<StandardFile> {
        return exec("find ${escapeQuotedAbsoluteFilePath(directory)} -path ${escapeQuotedAbsoluteFilePath(pruneDir)} -prune -o -print")
//        return exec("find " + escapeQuotedAbsoluteFilePath(directory) + " -mindepth 1" + " -path " + escapeQuotedAbsoluteFilePath(pruneDir) + " -prune -o -print")
    }


    @Throws(IOException::class)
    fun mv(source: File, target: File): Boolean {
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


    override fun stuffModifiedAfter(directory: StandardFile, pruneDir: StandardFile, timeStamp: Long): AutoKlausIterator<StandardFile> = AutoKlausIterator.EmptyAutoKlausIterator()

}
