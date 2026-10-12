package de.mel.android.filesync.bash

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns

import java.io.IOException

import de.mel.Lok
import de.mel.android.file.AndroidFile
import de.mel.android.file.AndroidFileConfiguration
import de.mel.auth.file.AbstractFile
import de.mel.auth.file.IFile
import de.mel.auth.file.StandardFile
import de.mel.filesync.bash.*
import java.io.File
import java.io.InputStreamReader

/**
 * Created by xor on 7/20/17.
 */

class BashToolsAndroid(private val context: Context) : BashTools<StandardFile>() {
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

    override fun stuffModifiedAfter(referenceFile: StandardFile, directory: StandardFile, pruneDir: StandardFile): List<StandardFile> {
        if (findNewerFallback != null)
            return findNewerFallback!!.stuffModifiedAfter(referenceFile, directory, pruneDir)
        StandardFile(referenceFile.absolutePath)
        return bashTolsUnix.stuffModifiedAfter(StandardFile(referenceFile.absolutePath), StandardFile(directory.absolutePath), StandardFile(pruneDir.absolutePath)).map { StandardFile(it.absolutePath) }
    }

    /**
     * There is no created date on Android. We use modified instead.
     */
    override fun createFsBashDetails(created: Long?, modified: Long, iNode: Long, symLink: Boolean, symLinkTarget: String?, name: String): FsBashDetails = FsBashDetails(modified, modified, iNode, symLink, symLinkTarget, name)

    private fun testCommands() {
        // find
        // in case find fails and we are on android 5+ we can use the storage access framework instead of the bash.
        // but in case it works we will stick to that
        val cacheDir = AbstractFile.instance(context.cacheDir)
        val dir = AbstractFile.instance(cacheDir, "bash.test")
        val prune = AbstractFile.instance(dir, "prune")
        val latestFile = AbstractFile.instance(dir, "file")
        var cmd = ""
        try {
            dir.mkdirs()
            prune.mkdirs()
            latestFile.createNewFile()
            cmd = "find \"" + dir.absolutePath + "\" -path " + bashTolsUnix.escapeQuotedAbsoluteFilePath(StandardFile(prune.absolutePath)) + " -prune -o -print"
            var streams = testCommand(cmd)
            var iterator: Iterator<IFile>? = streams.stdout
            while (iterator!!.hasNext()) {
                Lok.error("no SAF")
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
            cmd = "find \"" + cacheDir.absolutePath + "\" -newercc " + bashTolsUnix.escapeAbsoluteFilePath(StandardFile(dir.absolutePath)) + " -o -print"
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
        var stdout: AutoKlausIterator<IFile>? = null
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

//    @Throws(IOException::class)
//    override fun getFsBashDetails(file: AFile<*>): FsBashDetails {
//        val cmd = "ls -id " + escapeQuotedAbsoluteFilePath(file)
//        val args = arrayOf(BIN_PATH, "-c", cmd)
//        var line: String
//        try {
//            val proc = ProcessBuilder(*args).start()
//            val reader = BufferedReader(InputStreamReader(proc.inputStream))
//            line = reader.readLine()
//            line = line.trim { it <= ' ' }
//            val parts = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
//            val iNode = java.lang.Long.parseLong(parts[0])
//            /**
//             * Testing revealed that Android P (maybe even earlier) does not support creating symlinks via terminal.
//             * Thus supporting symlinks on Android is pointless.
//             */
//            return FsBashDetails(file.lastModified(), iNode, false, null, file.name)
//        } catch (e: Exception) {
//            Lok.error("could not execute:\n $cmd")
//            throw IOException(e)
//        }
//
//    }



    @Throws(IOException::class)
    override fun find(directory: StandardFile, pruneDir: StandardFile): AutoKlausIterator<StandardFile> {
        return if (findFallBack != null) findFallBack!!.find(directory, pruneDir) else findImpl(directory,pruneDir)
    }

    fun findImpl(directory: StandardFile, pruneDir: StandardFile): AutoKlausIterator<StandardFile> {
        return exec("find ${bashTolsUnix.escapeQuotedAbsoluteFilePath(StandardFile(directory.absolutePath))} -path ${bashTolsUnix.escapeQuotedAbsoluteFilePath(StandardFile(pruneDir.absolutePath))} -prune -o -print")
    }

    @Throws(IOException::class)
    private fun exec(cmd: String): AutoKlausIterator<StandardFile> {
        val args = arrayOf(BIN_PATH, "-c", cmd)
        Lok.debug("BashToolsAndroid.exec: $cmd")
        val proc = ProcessBuilder(*args).start()
        return BufferedIterator.BufferedFileIterator(InputStreamReader(proc.inputStream))
    }

    override fun setCreationDate(target: StandardFile, created: Long) {
        // android does not store creation dates
    }

    override fun getFsBashDetails(file: StandardFile): FsBashDetails? {
        return bashTolsUnix.getFsBashDetails(StandardFile(file.absolutePath))
    }

    override fun lnS(file: StandardFile, target: String) {
    }

    override fun rmRf(directory: StandardFile) {
        bashTolsUnix.rmRf(directory)
    }

    override fun stuffModifiedAfter(directory: StandardFile, pruneDir: StandardFile, timeStamp: Long): AutoKlausIterator<StandardFile> {
        throw NotImplementedError()
    }

    override fun getContentFsBashDetails(directory: StandardFile): MutableMap<String, FsBashDetails> {
        TODO("Not yet implemented")
    }
}
