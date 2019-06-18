package de.mein.drive.bash

import de.mein.Lok
import de.mein.auth.file.AFile
import de.mein.auth.tools.N
import de.mein.auth.file.DefaultFileConfiguration

import org.jdeferred.Promise
import org.jdeferred.impl.DeferredObject

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

/**
 * Created by xor on 13.07.2017.
 */
open class BashToolsUnix : BashToolsImpl {

    protected var BIN_PATH = "/bin/sh"
    private val executorService = Executors.newCachedThreadPool()


    val inotifyLimit: Long?
        @Throws(IOException::class)
        get() {
            val f = File("/proc/sys/fs/inotify/max_user_watches")
            val lines = Files.readAllLines(Paths.get(f.toURI()))
            return java.lang.Long.parseLong(lines[0])
        }


    @Throws(IOException::class)
    fun getModifiedAndInode(file: AFile<*>): ModifiedAndInode {
        val args = arrayOf(BIN_PATH, "-c", "stat -c %Y\" \"%i " + escapeAbsoluteFilePath(file))
        val proc = ProcessBuilder(*args).start()
        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        val line = reader.readLine()
        val split = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return ModifiedAndInode(java.lang.Long.parseLong(split[0]), java.lang.Long.parseLong(split[1]))
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
    protected fun escapeAbsoluteFilePath(file: AFile<*>): String {
        return ("\"" + file.absolutePath
                .replace("\"".toRegex(), "\\\\\"")
                .replace("`".toRegex(), "\\\\`")
                .replace("\\$".toRegex(), "\\\\\\$")
                + "\"")
    }

    @Throws(IOException::class, InterruptedException::class)
    override fun getModifiedAndINodeOfFile(file: AFile<*>): ModifiedAndInode {
        val args = arrayOf(BIN_PATH, "-c", "stat -c %i\\ %Y " + escapeAbsoluteFilePath(file))
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
        val parts = line!!.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val iNode = java.lang.Long.parseLong(parts[0])
        val modified = java.lang.Long.parseLong(parts[1])
        return ModifiedAndInode(modified, iNode)
    }

    /**
     * rm -rf
     *
     * @param directory
     */
    @Throws(IOException::class)
    override fun rmRf(directory: AFile<*>) {
        val args = arrayOf(BIN_PATH, "-c", "rm -rf " + escapeAbsoluteFilePath(directory))
        val proc = ProcessBuilder(*args).start()
        N.oneLine { proc.waitFor() }
    }

    @Throws(IOException::class, BashToolsException::class)
    override fun stuffModifiedAfter(referenceFile: AFile<*>, directory: AFile<*>, pruneDir: AFile<*>): List<AFile<*>> {
        Lok.debug("BashTools.stuffModifiedAfter: " + referenceFile.name + " mod: " + referenceFile.lastModified())
        //        String cmd = "find \"" + directory.getAbsolutePath() + "\"  "
        //                + " -path \"" + pruneDir + "\" -prune"
        //                + " -o -newer \"" + referenceFile.getAbsolutePath() + "\" -print";
        val cmd = ("find " + escapeAbsoluteFilePath(directory)
                + " -path " + escapeAbsoluteFilePath(pruneDir) + " -prune"
                + " -o -newer " + escapeAbsoluteFilePath(referenceFile) + " -print")
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
        return exec("find " + escapeAbsoluteFilePath(directory) + " -mindepth 1" + " -path " + escapeAbsoluteFilePath(pruneDir) + " -prune -o -print")
    }

    override fun getInode(f: AFile<*>): Promise<Long, Exception, Void> {
        val deferred = DeferredObject<Long, Exception, Void>()
        executorService.execute {
            N.r {
                val ba = "ls -i -d " + escapeAbsoluteFilePath(f)
                val args = arrayOf(BIN_PATH, "-c", ba)
                var inode: Long?
                var lines: List<String>
                var hasFinished = false
                var proc: Process? = null
                while (!hasFinished) {
                    try {
                        proc = ProcessBuilder(*args).start()
                        hasFinished = proc!!.waitFor(10, TimeUnit.SECONDS)
                        if (!hasFinished) {
                            val errorReader = BufferedReader(InputStreamReader(proc.errorStream))
//                            val errors = errorReader.lines().collect<List<String>, Any>(Collectors.toList())
                            Lok.debug("BashTools.stuffModifiedAfter.did not finish")
                        }
                        // try to read anyway.
                        // the process might have come to an end but Process.waitFor() does not always finish.
                        Lok.debug("BashTools.stuffModifiedAfter")
                        val reader = BufferedReader(InputStreamReader(proc.inputStream))
                        Lok.debug("BashTools.stuffModifiedAfter.collecting.result")
                        lines = reader.lines().collect(Collectors.toList())
//                        lines = reader.lines().collect<List<String>, Any>(Collectors.toList())
                        lines.forEach { s -> Lok.debug("BashTools.getNodeAndTime.LLLL $s") }
                        val s = lines[0].split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        if (s[0].isEmpty())
                            Lok.debug("BashTools.getNodeAndTime")
                        inode = java.lang.Long.parseLong(s[0])
                        deferred.resolve(inode)
                        hasFinished = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        proc!!.destroyForcibly()
                        continue
                    }

                }
            }
        }
        return deferred
    }

    override fun stuffModifiedAfter(directory: AFile<*>, pruneDir: AFile<*>, timeStamp: Long): Iterator<AFile<*>>? {
        System.err.println("BashToolsUnix.stuffModifiedAfter()... I AM THE UNIX GUY! >:(")
        return null
    }

    @Throws(IOException::class)
    override fun mkdir(dir: AFile<*>) {
        val args = arrayOf(BIN_PATH, "-c", "mkdir " + escapeAbsoluteFilePath(dir))
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

    @Throws(IOException::class, InterruptedException::class)
    fun countSubDirs(dir: File): Long? {
        var count: Long = 0L
        var finished = false
        val thread = Thread(Runnable {
            dir.walkTopDown().onEnter { file -> file.isDirectory }
                    .forEach { count++ }
            finished = true
        })
        thread.start()
        thread.join(10000)
        if (!finished)
            throw InterruptedException("counting subdirectories did not finish withing time limit")
        return count
    }

    companion object {


        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) {
            AFile.configure(DefaultFileConfiguration())
            val f = AFile.instance("f")
            f.mkdirs()
            val bashToolsUnix = BashToolsUnix()
            val modifiedAndInode = bashToolsUnix.getModifiedAndInode(f)
            Lok.debug("mod " + modifiedAndInode.modified + " ... inode " + modifiedAndInode.getiNode())
            f.delete()
        }
    }
}
