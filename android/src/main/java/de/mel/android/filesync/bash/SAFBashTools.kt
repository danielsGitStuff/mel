package de.mel.android.filesync.bash

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import de.mel.Lok
import de.mel.android.file.AndroidFile
import de.mel.android.file.AndroidFileConfiguration
import de.mel.android.file.SAFFile
import de.mel.android.file.SAFFileConfiguration
import de.mel.auth.file.AbstractFile
import de.mel.filesync.bash.AutoKlausIterator
import de.mel.filesync.bash.BashTools
import de.mel.filesync.bash.BashToolsException
import de.mel.filesync.bash.FsBashDetails
import java.io.File
import java.util.*

@RequiresApi(Build.VERSION_CODES.Q)
class SAFBashTools : BashTools<SAFFile>() {
    override fun getFsBashDetails(file: SAFFile): FsBashDetails? {
        val docFile = file.getDocFile()
        val uri = docFile!!.uri
        val contentResolver: ContentResolver = (AbstractFile.configuration as SAFFileConfiguration).context.contentResolver
        contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use {
                    it.moveToFirst()
                    val modified = it.getLong(it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
                    val inode = it.getLong(it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                    val name = it.getString(it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                    val fsBashDetails = FsBashDetails(modified, modified, inode, false, null, name)
                    return fsBashDetails
                }
        return null
    }


    override fun lnS(file: SAFFile, target: String) {
        throw BashToolsException("Android does not support symbolic links. This method must not be called!")
    }

    override fun getContentFsBashDetails(dir: SAFFile): MutableMap<String, FsBashDetails> {
        try {
            val thisDoc = dir.getDocFile()!!
            val uri: Uri = DocumentsContract.buildChildDocumentsUriUsingTree(thisDoc.uri, DocumentsContract.getDocumentId(thisDoc.uri))
            val contentResolver: ContentResolver = (AbstractFile.configuration as SAFFileConfiguration).context.contentResolver
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

    override fun rmRf(directory: SAFFile) {
    }

    override fun stuffModifiedAfter(referenceFile: SAFFile, directory: SAFFile, pruneDir: SAFFile): List<SAFFile> {
        throw BashToolsException("Method does not work on Android 10+ anymore.")
    }

    private class SAFFileHelper(val name: String, val mime: String) {
        fun isDir() = mime == DocumentsContract.Document.MIME_TYPE_DIR
    }

    override fun find(directory: SAFFile, pruneDir: SAFFile): AutoKlausIterator<SAFFile> {
        // todo saf the code currently ignores pruneDir until I found a way to recognize it via path without fucking with the documentfile cache
        // DocFileCache works best using DFS. Another thing SAF fucks up.
        val prunePath = pruneDir.absolutePath
        val config = AbstractFile.configuration as SAFFileConfiguration
        val cache = config.externalCache
        val docFile = directory.getDocFile()
        val uri = docFile!!.uri
        val contentResolver: ContentResolver = (AbstractFile.configuration as SAFFileConfiguration).context.contentResolver
        // Find does DFS, ContentResolver does not. What a nice day.

        contentResolver.query(directory.getChildrenUri(), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)
                ?.use { cursor ->
                    cursor.moveToFirst()
                    val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val klaus = object : AutoKlausIterator<SAFFile> {
                        /**
                         * Will keep an Iterator of sorted folder content for each step down.
                         */
                        lateinit var cursor: Cursor
                        val fileStack = Stack<SAFFile>()
                        val sortedFilesStack = Stack<Iterator<SAFFile>>()
                        override fun hasNext(): Boolean = sortedFilesStack.size > 0 && sortedFilesStack.peek().hasNext()

                        init {
                            fileStack.push(directory)
                        }

                        override fun next(): SAFFile {
                            while (!sortedFilesStack.peek().hasNext())
                                sortedFilesStack.pop()
                            return sortedFilesStack.peek().next()
                        }

                        fun readCursor(cursor: Cursor): Unit {
                            this.cursor = cursor
                            val parentFile = fileStack.peek()
                            val pathParts = AndroidFile.createRelativeFilePathParts(parentFile.storagePath, File(parentFile.absolutePath))
                            val list = mutableListOf<SAFFile>()
                            cursor.use {
                                it.moveToFirst()

                                while (!it.isAfterLast) {
                                    val mime = cursor.getString(mimeIndex)
                                    val name = cursor.getString(nameIndex)

                                    val file = AbstractFile.instance(parentFile, name) as SAFFile
                                    // filter prune directory
                                    if (!file.absolutePath.startsWith(prunePath))
                                        list.add(file)
                                    it.moveToNext()
                                }
                            }
                            // todo saf check if the contentresolver can sort the results
                            list.sortWith(Comparator { a, b ->
                                when {
                                    a.isDirectory == b.isDirectory -> a.getName().compareTo(b.getName())
                                    a.isDirectory -> -1
                                    else -> 1
                                }
                            })
//                            config.internalCache!!.findDoc(pathParts)
                            sortedFilesStack.push(list.iterator())
                        }

                        override fun close() {
                            Lok.debug("closed")
                        }
                    }
                    klaus.readCursor(cursor)
                    return klaus
                }
        return AutoKlausIterator.EmptyAutoKlausIterator()
    }

    override fun stuffModifiedAfter(directory: SAFFile, pruneDir: SAFFile, timeStamp: Long): AutoKlausIterator<SAFFile> {
        return AutoKlausIterator.EmptyAutoKlausIterator()
    }
}