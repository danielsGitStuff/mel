package de.mel.filesync.bash

import java.io.File
import java.io.IOException

import de.mel.auth.file.AbstractFile
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime

/**
 * Created by xor on 13.07.2017.
 */
abstract class BashToolsImpl {

//    abstract fun setBinPath(binPath: String)
//
//    @Throws(IOException::class, InterruptedException::class)
//    abstract fun getFsBashDetails(file: AbstractFile): FsBashDetails?
//
//    /**
//     * rm -rf
//     *
//     * @param directory
//     */
//    @Throws(IOException::class)
//    abstract fun rmRf(directory: AbstractFile)
//
//    open fun createFsBashDetails(created: Long?, modified: Long, iNode: Long, symLink: Boolean, symLinkTarget: String?, name: String): FsBashDetails {
//        return FsBashDetails(created, modified, iNode, symLink, symLinkTarget, name)
//    }
//
//    @Throws(IOException::class, BashToolsException::class)
//    open fun stuffModifiedAfter(referenceFile: AbstractFile, directory: AbstractFile, pruneDir: AbstractFile): List<AbstractFile> {
//        val time = referenceFile.lastModified()
//        val dir = File(directory.absolutePath)
//        val list = mutableListOf<AbstractFile>()
//        if (dir.exists())
//            dir.walkTopDown().onEnter { it.absolutePath != pruneDir.absolutePath }.filter { it.lastModified() >= time }.forEach {
//                list.add(AbstractFile.instance(it))
//            }
//        return list
//    }
//
//    @Throws(IOException::class)
//    abstract fun find(directory: AbstractFile, pruneDir: AbstractFile): AutoKlausIterator<AbstractFile>
//
//    @Throws(IOException::class, InterruptedException::class)
//    open fun stuffModifiedAfter(originalFile: AbstractFile, pruneDir: AbstractFile, time: Long): AutoKlausIterator<AbstractFile> {
//        val dir = File(originalFile.absolutePath)
//        if (dir.exists())
//            return object : AutoKlausIterator<AbstractFile> {
//                override fun close() {}
//
//                override fun hasNext(): Boolean = iterator.hasNext()
//
//                override fun next(): IFile = AbstractFile.instance(iterator.next())
//
//                override fun remove() {}
//
//                val iterator = dir.walkTopDown().onEnter { it.absolutePath != pruneDir.absolutePath }.filter { it.lastModified() >= time }.iterator()
//            }
//        return AutoKlausIterator.EmpyAutoKlausIterator()
//    }
//
//
//
//    open fun isSymLink(f: AbstractFile): Boolean = Files.isSymbolicLink(Paths.get(File(f.absolutePath).toURI()))
//
//    abstract fun getContentFsBashDetails(file: AbstractFile): Map<String, FsBashDetails>
//
//    abstract fun lnS(file: AbstractFile, target: String)
//    open fun setCreationDate(target: AbstractFile, created: Long) {
//        val path = Paths.get(File(target.absolutePath).toURI())
//        Files.setAttribute(path, "creationTime", FileTime.fromMillis(created))
//    }
}
