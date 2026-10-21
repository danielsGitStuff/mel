package de.mel.android.file

import android.content.ContentResolver
import de.mel.auth.file.AbstractFile
import de.mel.auth.file.AbstractFileWriter
import java.io.OutputStream

/**
 * @param append continue the file instead of replacing it
 */
class AndroidFileWriter(androidFile: AndroidFile, append: Boolean = false) : AbstractFileWriter() {
    override fun append(data: ByteArray, offset: Long, length: Int) {
        out?.write(data, 0, length)
    }

    override fun close() {
        out?.close()
    }

    val out: OutputStream?

    init {
        val contentResolver: ContentResolver = (AbstractFile.configuration as AndroidFileConfiguration).context.contentResolver
        if (!androidFile.exists())
            androidFile.createNewFile()
        val uri = androidFile.getDocFile()!!.uri
        out = contentResolver.openOutputStream(uri, if (append) "wa" else "wt")
    }
}