package de.mel.auth.file

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class StandardFileWriter(private val fileOutputStream: FileOutputStream?) : AbstractFileWriter() {
    override fun append(data: ByteArray, offset: Long, length: Int) {
        val ch: FileChannel? = fileOutputStream?.channel
        ch?.position(offset)
        ch?.write(ByteBuffer.wrap(data))
    }

    constructor(file: File) : this(FileOutputStream(file))

    override fun close() {
        fileOutputStream?.close()
    }
}