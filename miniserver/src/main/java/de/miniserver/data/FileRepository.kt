package de.miniserver.data

import java.io.File
import java.io.FileNotFoundException
import java.util.HashMap

class FileRepository {
    internal val hashFileMap = hashMapOf<String,File>()//HashMap<String, File>()

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


}
