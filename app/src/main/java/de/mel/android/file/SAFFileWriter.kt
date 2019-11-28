package de.mel.android.file

import android.content.ContentResolver
import android.os.Build
import androidx.annotation.RequiresApi
import de.mel.auth.file.AbstractFile
import de.mel.auth.file.AbstractFileWriter
import java.io.OutputStream


@RequiresApi(Build.VERSION_CODES.Q)
class SAFFileWriter(val file: SAFFile) : AbstractFileWriter() {
    val out: OutputStream?

    init {
        val contentResolver: ContentResolver = (AbstractFile.configuration as AndroidFileConfiguration).context.contentResolver
        if (!file.exists())
            file.createNewFile()
        val uri = file.getDocFile()!!.uri
        out = contentResolver.openOutputStream(uri, "w")
    }

    override fun append(data: ByteArray, offset: Long, length: Int) {
        /**
         * Though there is a way to append to files I choose not to use that method because the offset is provided by an Int which limits the maximum offset you can address to 2GB
         */
        out!!.write(data)
    }

    override fun close() {
        out?.close()
    }

}