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
import de.mel.filesync.bash.*
import de.mel.filesync.bash.BashToolsAndroidJavaImpl
import java.io.InputStreamReader

/**
 * Created by xor on 7/20/17.
 */

class BashToolsAndroid(private val context: Context) : BashToolsUnix() {
    private var findNewerFallback: BashToolsAndroidJavaImpl? = null
    private val javaBashTools: BashToolsAndroidJavaImpl
    private var findFallBack: BashToolsAndroidJavaImpl? = null


    init {
        readCreated = false
        BIN_PATH = "/system/bin/sh"
        javaBashTools = BashToolsAndroidJavaImpl()
        testCommands()
    }

    override fun stuffModifiedAfter(referenceFile: AbstractFile, directory: AbstractFile, pruneDir: AbstractFile): List<AbstractFile> {
        if (findNewerFallback != null)
            return findNewerFallback!!.stuffModifiedAfter(referenceFile, directory, pruneDir)
        return super.stuffModifiedAfter(referenceFile, directory, pruneDir)
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
            cmd = "find \"" + dir.absolutePath + "\" -path " + escapeQuotedAbsoluteFilePath(prune) + " -prune -o -print"
            var streams = testCommand(cmd)
            var iterator: Iterator<AbstractFile>? = streams.stdout
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
        var stdout: AutoKlausIterator<AbstractFile<*>>? = null
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

    override fun getContentFsBashDetails(directory: AbstractFile): MutableMap<String, FsBashDetails> {
        val dir = directory as AndroidFile
        // this does not work from Android 10 onwards
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            return super.getContentFsBashDetails(directory)
//        val d = FsBashDetails(created,modified,inode,issymlink,symlinktarget,name)

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
    override fun find(directory: AbstractFile, pruneDir: AbstractFile): AutoKlausIterator<AbstractFile> {
        return if (findFallBack != null) findFallBack!!.find(directory, pruneDir) else super.find(directory, pruneDir)
    }

    override fun setCreationDate(target: AbstractFile, created: Long) {
        // android does not store creation dates
    }
}
