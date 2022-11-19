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

class BashToolsAndroid(private val context: Context) : BashTools<AndroidFile>() {
    private var findNewerFallback: BashToolsAndroidJavaImpl? = null
    private val javaBashTools: BashToolsAndroidJavaImpl
    private var findFallBack: BashToolsAndroidJavaImpl? = null
    val bashTolsUnix = BashToolsUnix()
    private var BIN_PATH: String = "/system/bin/sh"

    init {
        javaBashTools = BashToolsAndroidJavaImpl()
        testCommands()
    }

    override fun stuffModifiedAfter(referenceFile: AndroidFile, directory: AndroidFile, pruneDir: AndroidFile): List<AndroidFile> {
        if (findNewerFallback != null)
            return findNewerFallback!!.stuffModifiedAfter(referenceFile, directory, pruneDir)
        StandardFile(referenceFile.absolutePath)
        return bashTolsUnix.stuffModifiedAfter(StandardFile(referenceFile.absolutePath), StandardFile(directory.absolutePath), StandardFile(pruneDir.absolutePath)).map { AndroidFile(it.absolutePath) }
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

    override fun getContentFsBashDetails(directory: AndroidFile): MutableMap<String, FsBashDetails> {
        val dir = directory as AndroidFile
        try {
            val thisDoc = dir.getDocFile()!!
            val uri: Uri = DocumentsContract.buildChildDocumentsUriUsingTree(thisDoc.uri, DocumentsContract.getDocumentId(thisDoc.uri))
            val contentResolver: ContentResolver = (AbstractFile.configuration as AndroidFileConfiguration).context.contentResolver
            val content = mutableMapOf<String, FsBashDetails>()
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME
                    , DocumentsContract.Document.COLUMN_LAST_MODIFIED
                    , DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null, null)?.use {
                it.moveToFirst()
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val modIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val docIdIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                while (!it.isAfterLast) {
                    val name = it.getString(nameIndex)
                    val modified = it.getLong(modIndex)
                    val docId = it.getLong(docIdIndex)
                    val details = FsBashDetails(modified, modified, docId, false, null, name)
                    content[name] = details
                    it.moveToNext()
                }
            }
            return content
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return mutableMapOf()
    }

    @Throws(IOException::class)
    override fun find(directory: AndroidFile, pruneDir: AndroidFile): AutoKlausIterator<AndroidFile> {
        return if (findFallBack != null) findFallBack!!.find(directory, pruneDir) else findImpl(directory,pruneDir)
    }

    fun findImpl(directory: AndroidFile, pruneDir: AndroidFile): AutoKlausIterator<AndroidFile> {
        return exec("find ${bashTolsUnix.escapeQuotedAbsoluteFilePath(StandardFile(directory.absolutePath))} -path ${bashTolsUnix.escapeQuotedAbsoluteFilePath(StandardFile(pruneDir.absolutePath))} -prune -o -print")
    }

    @Throws(IOException::class)
    private fun exec(cmd: String): AutoKlausIterator<AndroidFile> {
        val args = arrayOf(BIN_PATH, "-c", cmd)
        Lok.debug("BashToolsAndroid.exec: $cmd")
        val proc = ProcessBuilder(*args).start()
        return BufferedIterator.BufferedFileIterator(InputStreamReader(proc.inputStream))
    }

    override fun setCreationDate(target: AndroidFile, created: Long) {
        // android does not store creation dates
    }

    override fun getFsBashDetails(file: AndroidFile): FsBashDetails? {
        return bashTolsUnix.getFsBashDetails(StandardFile(file.absolutePath))
    }

    override fun lnS(file: AndroidFile, target: String) {
    }

    override fun rmRf(directory: AndroidFile) {
        throw BashToolsException.NotImplemented()
    }

    override fun stuffModifiedAfter(directory: AndroidFile, pruneDir: AndroidFile, timeStamp: Long): AutoKlausIterator<AndroidFile> {
        throw NotImplementedError()
    }
}
