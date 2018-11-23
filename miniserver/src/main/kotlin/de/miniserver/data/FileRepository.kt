package de.miniserver.data

import java.io.File
import java.io.FileNotFoundException

class FileRepository {
    internal val hashFileMap = hashMapOf<String, File>()//HashMap<String, File>()
    private val hashBytesMap = hashMapOf<String, ByteArray>()

    fun addEntry(hash: String, file: File): FileRepository {
        hashFileMap[hash] = file
        return this
    }

    @Throws(Exception::class)
    fun getFile(hash: String): File {
        if (hashFileMap.containsKey(hash))
            return hashFileMap[hash]!!
        throw FileNotFoundException("no file for " + (hash ?: "null"))
    }

    @Throws(java.lang.Exception::class)
    fun getBytes(hash: String): ByteArray {
        if (hashBytesMap.containsKey(hash))
            return hashBytesMap[hash]!!
        val f = getFile(hash)
        val bytes = f.inputStream().readBytes()
        hashBytesMap[hash] = bytes
        return bytes
    }

}
