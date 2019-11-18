package de.mel.android.filesync.bash

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import de.mel.android.file.AndroidFile
import de.mel.android.file.AndroidFileConfiguration
import de.mel.auth.file.AbstractFile
import de.mel.filesync.bash.AutoKlausIterator
import de.mel.filesync.bash.BashTools
import de.mel.filesync.bash.FsBashDetails

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class SAFBashTools:BashTools<AndroidFile>() {
    override fun getFsBashDetails(file: AndroidFile): FsBashDetails? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }



    override fun lnS(file: AndroidFile, target: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getContentFsBashDetails(dir: AndroidFile): MutableMap<String, FsBashDetails> {
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
        return mutableMapOf()    }

    override fun rmRf(directory: AndroidFile) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun stuffModifiedAfter(referenceFile: AndroidFile, directory: AndroidFile, pruneDir: AndroidFile): List<AndroidFile> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun find(directory: AndroidFile, pruneDir: AndroidFile): AutoKlausIterator<AndroidFile> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun stuffModifiedAfter(directory: AndroidFile, pruneDir: AndroidFile, timeStamp: Long): AutoKlausIterator<AndroidFile> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}