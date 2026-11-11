package de.mel.auth.file

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class StandardFileWriter private constructor(private val channel: FileChannel?, private val closeable: Closeable?) : AbstractFileWriter() {
    constructor(fileOutputStream: FileOutputStream?) : this(fileOutputStream?.channel, fileOutputStream)

    constructor(randomAccessFile: RandomAccessFile) : this(randomAccessFile.channel, randomAccessFile)

    constructor(file: File) : this(FileOutputStream(file))

    companion object {
        @Throws(java.io.IOException::class)
        @JvmStatic
        @JvmOverloads
        fun open(file: File, position: Long = 0L): StandardFileWriter? {
            if (position == 0L) {
                return StandardFileWriter(file)
            }
            if (position < 0L || !file.exists() || file.length() < position)
                return null
            val randomAccessFile = RandomAccessFile(file, "rw")
            try {
                randomAccessFile.setLength(position)
            } catch (e: java.io.IOException) {
                randomAccessFile.close()
                throw e
            }
            return StandardFileWriter(randomAccessFile)
        }
    }

    override fun append(data: ByteArray, offset: Long, length: Int) {
        val ch = channel ?: return
        val buffer = ByteBuffer.wrap(data, 0, length)
        var position = offset
        while (buffer.hasRemaining())
            position += ch.write(buffer, position)
    }

    override fun close() {
        closeable?.close()
    }
}
