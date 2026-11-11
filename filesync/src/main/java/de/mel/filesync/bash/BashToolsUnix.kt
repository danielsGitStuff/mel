package de.mel.filesync.bash

import de.mel.Lok
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
open class BashToolsUnix : BashTools() {
    override fun lnS(file: File, target: String) {
        val proc = ProcessBuilder("ln", "-s", File(target).absolutePath, file.absolutePath).start()
        proc.waitFor()
    }

    /** makes a process return the result of the actual command in English. */
    private fun ProcessBuilder.unfrench(): ProcessBuilder {
        environment()["LC_ALL"] = "C"
        return this
    }

    var readCreated: Boolean = true

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
    fun escapeQuotedAbsoluteFilePath(file: File): String {
        return ("\"${escapeAbsoluteFilePath(file)}\"")
    }

    /**
     * escapes this character: "
     *
     * @param file
     * @return
     */
    fun escapeAbsoluteFilePath(file: File): String {
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
    override fun getFsBashDetails(file: File): FsBashDetails? {
        val args = arrayOf("stat", "-c", "%i %W %F", file.absolutePath)
        val proc = ProcessBuilder(*args).unfrench().start()
//        proc.waitFor(); // this line sometimes hangs. Process.exitcode is 0 and Process.hasExited is false
        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        val line = reader.readLine()
        if (line == null) {
            Lok.debug("reading error for: " + file.absolutePath)
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
        if (!file.exists()) {
            throw IOException("file/dir ${file.absolutePath} does not exist")
        }
        if (line == null)
            throw IOException("stat returned nothing for ${file.absolutePath}")
        val parts = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val iNode = java.lang.Long.parseLong(parts[0])
        val created = if (readCreated) java.lang.Long.parseLong(parts[1]) * 1000 else null
        val symLink = parts[2].startsWith("sym")
        val name = file.getName()
        val modified = file.lastModified()
        val symLinkTarget: String? = if (symLink)
            parseSymLink(file, Files.readSymbolicLink(Paths.get(file.absolutePath)).toString())
        else
            null
        return createFsBashDetails(created, modified!!, iNode, symLink, symLinkTarget, name)
    }

    /**
     * parses an output line from stat
     */
    private fun parseSymLink(file: File, line: String): String {
        var symLinkTarget: String? = null
        val originalSym = line
        val parentCanonical = file.getParentFile()!!.canonicalPath
        val completePath = parentCanonical + File.separator + originalSym
        val ComplecteCanonical = File(completePath).canonicalPath
        symLinkTarget = ComplecteCanonical.drop(parentCanonical!!.length)

        if (symLinkTarget == "")
            symLinkTarget = "."
        if (symLinkTarget.startsWith(File.separator))
            symLinkTarget = symLinkTarget.drop(1)
        return symLinkTarget
    }

    override fun getContentFsBashDetails(directory: File): MutableMap<String, FsBashDetails> {
        val map = mutableMapOf<String, FsBashDetails>()
        // the shell expands the globs; the directory is passed as $1 so it needs no escaping.
        // the second glob also returns "." and ".."
        val cmd = "stat -c '%n|%i|%W|%F|%Y' \"$1\"/* \"$1\"/.* 2>/dev/null"
        val proc = ProcessBuilder(BIN_PATH, "-c", cmd, "_", directory.absolutePath).unfrench().start()
        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        var line: String? = reader.readLine()
        while (line != null) {
            val parts = line.split("|")
            val absolute = parts[0]
            val iNode = parts[1].toLong()
            val created = if (readCreated) parts[2].toLong() * 1000 else null
            val symLink = parts[3].startsWith("sym")
            val modified = parts[4].toLong() * 1000
            val file = File(absolute)
            val name = file.name
            // skip "." and ".."
            if (name.isNotEmpty() && name != "." && name != "..") {
                val symLinkTarget: String? = if (symLink)
                    try {
                        parseSymLink(file, Files.readSymbolicLink(Paths.get(file.absolutePath)).toString())
                    } catch (e: Exception) {
                        null
                    }
                else
                    null
                val details = createFsBashDetails(created, modified, iNode, symLink, symLinkTarget, name)
                map[name] = details
            }
            line = reader.readLine()
        }
        return map
    }

    /**
     * rm -rf
     *
     * @param directory
     */
    @Throws(IOException::class)
    override fun rmRf(directory: File) {
        val args = arrayOf(BIN_PATH, "-c", "rm -rf " + escapeQuotedAbsoluteFilePath(directory))
        val proc = ProcessBuilder(*args).start()
        N.oneLine { proc.waitFor() }
    }

    @Throws(IOException::class, BashToolsException::class)
    override fun stuffModifiedAfter(referenceFile: File, directory: File, pruneDir: File): List<File> {
        Lok.debug("BashTools.Companion.stuffModifiedAfter: " + referenceFile.getName() + " mod: " + referenceFile.lastModified())
        val cmd = ("find " + escapeQuotedAbsoluteFilePath(directory)
                + " -path " + escapeQuotedAbsoluteFilePath(pruneDir) + " -prune"
                + " -o -newercc " + escapeQuotedAbsoluteFilePath(referenceFile) + " -print")
        Lok.debug("BashTools.Companion.stuffModifiedAfter.cmd: $cmd")
        val args = arrayOf(BIN_PATH, "-c", cmd)
        val processBuilder = ProcessBuilder(*args)
        processBuilder.redirectErrorStream(true)
        val proc = processBuilder.start()
        Lok.debug("BashTools.Companion.stuffModifiedAfter.collecting.result")
        val result = ArrayList<File>()
        val iterator = BufferedIterator.BufferedFileIterator(InputStreamReader(proc.inputStream))
        while (iterator.hasNext()) {
            val path = iterator.next()
            result.add(path)
        }
        Lok.debug("BashTools.Companion.stuffModifiedAfter.collecting.done")
        return result
    }

    @Throws(IOException::class)
    private fun exec(cmd: String): AutoKlausIterator<File> {
        val args = arrayOf(BIN_PATH, "-c", cmd)
        Lok.debug("BashToolsUnix.exec: $cmd")
        val proc = ProcessBuilder(*args).start()
        return BufferedIterator.BufferedFileIterator(InputStreamReader(proc.inputStream))
    }

    @Throws(IOException::class)
    override fun find(directory: File, pruneDir: File): AutoKlausIterator<File> {
        return exec("find ${escapeQuotedAbsoluteFilePath(directory)} -path ${escapeQuotedAbsoluteFilePath(pruneDir)} -prune -o -print")
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


    /**
     * Not every `find` takes a time stamp: this one does if it understands the time in the format that GNU find and bfs have in common (ISO 8601, UTC, whole seconds)
     * and compares it with the change time of a file. Tried once.
     */
    private val findByTime: Boolean by lazy {
        val dir = Files.createTempDirectory("mel.find.test").toFile()
        try {
            val proc = ProcessBuilder(BIN_PATH, "-c", "find ${escapeQuotedAbsoluteFilePath(dir)} -newerct 1970-01-01T00:00:00Z -print").redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor() == 0 && out.trim() == dir.absolutePath
        } catch (e: Exception) {
            false
        } finally {
            dir.delete()
        }
    }

    override fun canFindByTime(): Boolean = findByTime

    /**
     * `find` by the change time (ctime): what is new, modified, renamed or copied with an old modified time. [timeStamp] is rounded down to the second, so a bit more is found, never less.
     */
    private fun findChangedAfter(directory: File, pruneDir: File, timeStamp: Long): AutoKlausIterator<File> {
        // year 9999. Nothing is changed after that, and the format ends there
        if (timeStamp > 253402300799000L)
            return object : AutoKlausIterator<File> {
                override fun hasNext(): Boolean = false
                override fun next(): File = throw NoSuchElementException()
                override fun close() {}
            }
        val time = java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.ofEpochSecond(Math.floorDiv(Math.max(timeStamp, 0L), 1000L)))
        return exec("find ${escapeQuotedAbsoluteFilePath(directory)} -path ${escapeQuotedAbsoluteFilePath(pruneDir)} -prune -o -newerct $time -print")
    }

    /**
     * Everything below [directory] that was modified after [timeStamp] (ms), except what lives in [pruneDir].
     * [directory] itself is included if it was modified at or after [timeStamp]. Same semantics as the Windows implementation.
     */
    override fun stuffModifiedAfter(directory: File, pruneDir: File, timeStamp: Long): AutoKlausIterator<File> {
        if (canFindByTime())
            return findChangedAfter(directory, pruneDir, timeStamp)
        val root = Paths.get(directory.absolutePath)
        val prune = pruneDir.absolutePath
        val stream = Files.walk(root)
        val iterator = stream
            .filter { path ->
                val f = path.toFile()
                val pruned = f.absolutePath == prune || f.absolutePath.startsWith(prune + File.separator)
                val modified = f.lastModified()
                !pruned && if (path == root) modified >= timeStamp else modified > timeStamp
            }
            .map { it.toFile() }
            .iterator()
        return object : AutoKlausIterator<File> {
            override fun hasNext(): Boolean = iterator.hasNext()
            override fun next(): File = iterator.next()
            override fun close() = stream.close()
        }
    }


}
