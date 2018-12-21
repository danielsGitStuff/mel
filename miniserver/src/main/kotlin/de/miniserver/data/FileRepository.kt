package de.miniserver.data

import java.io.File
import java.io.FileNotFoundException

fun main(args: Array<String>) {
    val fileEntry = FileEntry(hash = "h",file = File("bla"),variant = "var",version = 666L)
    val repo = FileRepository()
    repo += fileEntry
    val r = repo["h"]
    println(r)
}

@Suppress("ArrayInDataClass")
class FileEntry(val hash: String, val file: File, val variant: String, val version: Long, bytes: ByteArray? = null) {
    var bytes: ByteArray? = bytes
        get() {
            if (field == null) {
                field = file.inputStream().readBytes()
            }
            return field!!
        }

}

/**
 * This is fun with operator overloading. Because I can.
 */
class FileRepository {


    internal val hashFileMap = hashMapOf<String, FileEntry>()//HashMap<String, File>()

    @Throws(FileNotFoundException::class)
    operator fun get(hash: String): FileEntry {
        checkHashKey(hash)
        return hashFileMap[hash]!!
    }

    operator fun set(hash: String, fileEntry: FileEntry) {
        if (hashFileMap.containsKey(hash))
            throw Exception("duplicate file")
        hashFileMap[hash] = fileEntry
    }

    operator fun plusAssign(fileEntry: FileEntry) {
        set(fileEntry.hash, fileEntry)
    }

    operator fun minusAssign(fileEntry: FileEntry){
        checkHashKey(fileEntry.hash)
        hashFileMap.remove(fileEntry.hash)
    }

    operator fun minusAssign(hash: String){
        checkHashKey(hash)
        hashFileMap.remove(hash)
    }

    @Throws(FileNotFoundException::class)
    private fun checkHashKey(hash: String) {
        if (!hashFileMap.containsKey(hash))
            throw FileNotFoundException("no file for $hash")
    }
}
