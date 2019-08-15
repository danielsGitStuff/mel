package de.mein.android.drive.bash

import android.content.Context

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

import de.mein.Lok
import de.mein.auth.file.AFile
import de.mein.drive.bash.*
import de.mein.drive.bash.BashToolsAndroidJavaImpl

/**
 * Created by xor on 7/20/17.
 */

class BashToolsAndroid(private val context: Context) : BashToolsUnix() {
    private var findNewerFallback: BashToolsAndroidJavaImpl? = null
    private val javaBashTools: BashToolsAndroidJavaImpl
    private var findFallBack: BashToolsAndroidJavaImpl? = null


    init {
        BIN_PATH = "/system/bin/sh"
        javaBashTools = BashToolsAndroidJavaImpl()
        testCommands()
    }

    override fun stuffModifiedAfter(referenceFile: AFile<*>, directory: AFile<*>, pruneDir: AFile<*>): List<AFile<*>> {
        if (findNewerFallback != null)
            return findNewerFallback!!.stuffModifiedAfter(referenceFile, directory, pruneDir)
        return super.stuffModifiedAfter(referenceFile, directory, pruneDir)
    }

    private fun testCommands() {
        // find
        // in case find fails and we are on android 5+ we can use the storage access framework instead of the bash.
        // but in case it works we will stick to that
        val cacheDir = AFile.instance(context.cacheDir)
        val dir = AFile.instance(cacheDir, "bash.test")
        val prune = AFile.instance(dir, "prune")
        val latestFile = AFile.instance(dir, "file")
        var cmd = ""
        try {
            dir.mkdirs()
            prune.mkdirs()
            latestFile.createNewFile()
            cmd = "find \"" + dir.absolutePath + "\" -path " + escapeQuotedAbsoluteFilePath(prune) + " -prune -o -print"
            var streams = testCommand(cmd)
            var iterator: Iterator<AFile<*>>? = streams.stdout
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
            cmd = "find \"" + cacheDir.absolutePath + "\" -newercc " + escapeAbsoluteFilePath(dir) + " -o -print"
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
        var stdout: Iterator<AFile<*>>? = null
        var stderr: Iterator<String>? = null
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
        streams.stdout = BashTools.inputStreamToFileIterator(proc.inputStream)
        streams.stderr = BashTools.inputStreamToIterator(proc.errorStream)
        return streams
    }

    @Throws(IOException::class)
    override fun getFsBashDetails(file: AFile<*>): FsBashDetails {
        val cmd = "ls -id " + escapeQuotedAbsoluteFilePath(file)
        val args = arrayOf(BIN_PATH, "-c", cmd)
        var line: String
        try {
            val proc = ProcessBuilder(*args).start()
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            line = reader.readLine()
            line = line.trim { it <= ' ' }
            val parts = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val iNode = java.lang.Long.parseLong(parts[0])
            /**
             * Testing revealed that Android P (maybe even earlier) does not support creating symlinks via terminal.
             * Thus supporting symlinks on Android is pointless.
             */
            return FsBashDetails(file.lastModified(), iNode, false, null, file.name)
        } catch (e: Exception) {
            Lok.error("could not execute:\n $cmd")
            throw IOException(e)
        }

    }

    @Throws(IOException::class)
    override fun find(directory: AFile<*>, pruneDir: AFile<*>): AutoKlausIterator<AFile<*>> {
        return if (findFallBack != null) findFallBack!!.find(directory, pruneDir) else super.find(directory, pruneDir)
    }
}
