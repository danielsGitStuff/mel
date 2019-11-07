package de.mel.auth.file

abstract class AbstractFileWriter {
    abstract fun append(data: ByteArray, offset: Long, length: Int)
    fun append(data: ByteArray, offset: Long) {
        append(data, offset, data.size)
    }

    abstract fun close()
}