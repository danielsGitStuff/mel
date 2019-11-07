package de.mel.android.file

import android.content.ContentResolver
import de.mel.auth.file.AbstractFile
import de.mel.auth.file.AbstractFileWriter
import java.io.OutputStream
import java.nio.ByteBuffer

class AndroidFileWriter(androidFile: AndroidFile) : AbstractFileWriter() {
    override fun append(offset: Long, data: ByteArray) {
        out?.write(data)
    }

    override fun close() {
        out?.close()
    }

    val out: OutputStream?

    init {
        val contentResolver: ContentResolver = (AbstractFile.getConfiguration() as AndroidFileConfiguration).context.contentResolver
        val uri = androidFile.createDocFile()!!.uri
        out = contentResolver.openOutputStream(uri, "wa")
    }
}