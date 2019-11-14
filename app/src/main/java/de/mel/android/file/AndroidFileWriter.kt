package de.mel.android.file

import android.content.ContentResolver
import de.mel.auth.file.AbstractFile
import de.mel.auth.file.AbstractFileWriter
import java.io.OutputStream

class AndroidFileWriter(androidFile: AndroidFile) : AbstractFileWriter() {
    override fun append(data: ByteArray, offset: Long, length: Int) {
        out?.write(data)
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
        out = contentResolver.openOutputStream(uri, "wa")
    }
}