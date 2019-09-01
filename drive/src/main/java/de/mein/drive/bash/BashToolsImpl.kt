package de.mein.drive.bash

import de.mein.drive.sql.FsEntry
import org.jdeferred.Promise

import java.io.File
import java.io.IOException

import de.mein.auth.file.AFile
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Created by xor on 13.07.2017.
 */
abstract class BashToolsImpl {

    abstract fun setBinPath(binPath: String)

    @Throws(IOException::class, InterruptedException::class)
    abstract fun getFsBashDetails(file: AFile<*>): FsBashDetails?

    /**
     * rm -rf
     *
     * @param directory
     */
    @Throws(IOException::class)
    abstract fun rmRf(directory: AFile<*>)

    @Throws(IOException::class, BashToolsException::class)
    open fun stuffModifiedAfter(referenceFile: AFile<*>, directory: AFile<*>, pruneDir: AFile<*>): List<AFile<*>> {
        val time = referenceFile.lastModified()
        val dir = File(directory.absolutePath)
        val list = mutableListOf<AFile<*>>()
        if (dir.exists())
            dir.walkTopDown().onEnter { it.absolutePath != pruneDir.absolutePath }.filter { it.lastModified() >= time }.forEach {
                list.add(AFile.instance(it))
            }
        return list
    }

    @Throws(IOException::class)
    abstract fun find(directory: AFile<*>, pruneDir: AFile<*>): AutoKlausIterator<AFile<*>>

    @Throws(IOException::class, InterruptedException::class)
    open fun stuffModifiedAfter(originalFile: AFile<*>, pruneDir: AFile<*>, time: Long): AutoKlausIterator<AFile<*>> {
        val dir = File(originalFile.absolutePath)
        if (dir.exists())
            return object : AutoKlausIterator<AFile<*>> {
                override fun close() {}

                override fun hasNext(): Boolean = iterator.hasNext()

                override fun next(): AFile<*> = AFile.instance(iterator.next())

                override fun remove() {}

                val iterator = dir.walkTopDown().onEnter { it.absolutePath != pruneDir.absolutePath }.filter { it.lastModified() >= time }.iterator()
            }
        return AutoKlausIterator.EmpyAutoKlausIterator()
    }

    @Throws(IOException::class)
    abstract fun mkdir(dir: AFile<*>)


    open fun isSymLink(f: AFile<*>): Boolean = Files.isSymbolicLink(Paths.get(File(f.absolutePath).toURI()))

    abstract fun getContentFsBashDetails(file: AFile<*>): Map<String, FsBashDetails>

    abstract fun lnS(file: AFile<*>, target: String)
}
