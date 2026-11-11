package de.mel.android.filesync.bash

import android.content.Context
import java.io.IOException

import de.mel.Lok
import de.mel.filesync.bash.*
import java.io.File
import java.io.InputStreamReader

/**
 * Created by xor on 7/20/17.
 */

class BashToolsAndroid(private val context: Context) : BashTools() {
    private var findNewerFallback: BashToolsAndroidJavaImpl? = null
    private val javaBashTools: BashToolsAndroidJavaImpl
    private var findFallBack: BashToolsAndroidJavaImpl? = null
    val bashTolsUnix = BashToolsUnix()
    private var BIN_PATH: String = "/system/bin/sh"

    init {
        bashTolsUnix.setBinPath("sh")
        bashTolsUnix.readCreated = false
        javaBashTools = BashToolsAndroidJavaImpl()
        testCommands()
    }

    override fun stuffModifiedAfter(referenceFile: File, directory: File, pruneDir: File): List<File> {
        if (findNewerFallback != null)
            return findNewerFallback!!.stuffModifiedAfter(referenceFile, directory, pruneDir)
        return bashTolsUnix.stuffModifiedAfter(referenceFile, directory, pruneDir)
    }

    /**
     * There is no created date on Android. We use modified instead.
     */
    override fun createFsBashDetails(created: Long?, modified: Long, iNode: Long, symLink: Boolean, symLinkTarget: String?, name: String): FsBashDetails = FsBashDetails(modified, modified, iNode, symLink, symLinkTarget, name)

    private fun testCommands() {
        val cacheDir = context.cacheDir
        val dir = File(cacheDir, "bash.test")
        val prune = File(dir, "prune")
        val latestFile = File(dir, "file")
        var cmd = ""
        try {
            dir.mkdirs()
            prune.mkdirs()
            latestFile.createNewFile()
            cmd = "find \"" + dir.absolutePath + "\" -path " + bashTolsUnix.escapeQuotedAbsoluteFilePath(prune) + " -prune -o -print"
            var streams = testCommand(cmd)
            var iterator: Iterator<File>? = streams.stdout
            while (iterator!!.hasNext()) {
                val line = iterator.next()
                if (line.absolutePath == prune.absolutePath) {
                    throw BashToolsException("'find' ignored '-prune")
                }
            }
            while (streams.stderr!!.hasNext())
                Lok.error("testCommands(): " + streams.stderr!!.next())
        } catch (e: Exception) {
            Lok.error("did not work as expected: $cmd")
            Lok.error("using.fallback.for 'find'")
            findFallBack = javaBashTools
            e.printStackTrace()
        }
        try {
            cmd = "find \"" + cacheDir.absolutePath + "\" -newercc " + bashTolsUnix.escapeAbsoluteFilePath(dir) + " -o -print"
            val streams = testCommand(cmd)
            val iterator = streams.stdout
            while (iterator!!.hasNext()) {
                val line = iterator.next()
                if (line.absolutePath != latestFile.absolutePath)
                    throw java.lang.Exception("-newercc not respected")
            }
        } catch (e: Exception) {
            Lok.error("did not work as expected: $cmd")
            Lok.error("using.fallback.for 'find -newercc'")
            findNewerFallback = javaBashTools
        }

    }

    internal inner class Streams {
        var stdout: AutoKlausIterator<File>? = null
        var stderr: AutoKlausIterator<String>? = null
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun testCommand(cmd: String): Streams {
        val args = arrayOf(BIN_PATH, "-c", cmd)
        Lok.debug("BashToolsUnix.exec: $cmd")
        val proc = ProcessBuilder(*args).start()
        proc.waitFor()
        if (proc.exitValue() != 0) {
            throw BashToolsException(proc)
        }
        Lok.debug("BashTest.exec." + proc.exitValue())
        val streams = Streams()
        streams.stdout = BashTools.Companion.inputStreamToFileIterator(proc.inputStream)
        streams.stderr = BufferedIterator.BufferedStringIterator(InputStreamReader(proc.errorStream))
        return streams
    }

    @Throws(IOException::class)
    override fun find(directory: File, pruneDir: File): AutoKlausIterator<File> {
        return if (findFallBack != null) findFallBack!!.find(directory, pruneDir) else findImpl(directory, pruneDir)
    }

    fun findImpl(directory: File, pruneDir: File): AutoKlausIterator<File> {
        return exec("find ${bashTolsUnix.escapeQuotedAbsoluteFilePath(directory)} -path ${bashTolsUnix.escapeQuotedAbsoluteFilePath(pruneDir)} -prune -o -print")
    }

    @Throws(IOException::class)
    private fun exec(cmd: String): AutoKlausIterator<File> {
        val args = arrayOf(BIN_PATH, "-c", cmd)
        Lok.debug("BashToolsAndroid.exec: $cmd")
        val proc = ProcessBuilder(*args).start()
        return BufferedIterator.BufferedFileIterator(InputStreamReader(proc.inputStream))
    }

    override fun setCreationDate(target: File, created: Long) {
        // android does not store creation dates
    }

    override fun getFsBashDetails(file: File): FsBashDetails? {
        return bashTolsUnix.getFsBashDetails(file)
    }

    override fun lnS(file: File, target: String) {
    }

    override fun rmRf(directory: File) {
        bashTolsUnix.rmRf(directory)
    }

    override fun stuffModifiedAfter(directory: File, pruneDir: File, timeStamp: Long): AutoKlausIterator<File> {
        throw NotImplementedError()
    }

    override fun getContentFsBashDetails(directory: File): MutableMap<String, FsBashDetails> {
        TODO("Not yet implemented")
    }
}
