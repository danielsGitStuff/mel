package de.mel.filesync.bash

import de.mel.filesync.bash.BufferedIterator.BufferedFileIterator
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime


abstract class BashTools {
    abstract fun getFsBashDetails(file: File): FsBashDetails?

    companion object {
        var binPath: String? = null
        val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")
        var implementation: BashTools = if (isWindows) {
            BashToolsWindows()
        } else {
            BashToolsUnix()
        }
            get

        fun getUnixImplementation(): BashToolsUnix? {
            return if (implementation is BashToolsUnix) implementation as BashToolsUnix else null
        }

        fun getFsBashDetails(file: File): FsBashDetails? = implementation.getFsBashDetails(file)

        fun init() {

        }

        fun inputStreamToFileIterator(inputStream: InputStream): AutoKlausIterator<File> {
            return BufferedFileIterator(InputStreamReader(inputStream))
        }

        fun rmRf(file: File) = implementation.rmRf(file)

        fun find(root: File, pruneDir: File): AutoKlausIterator<File> = implementation.find(root, pruneDir)
        fun isSymLink(file: File) = implementation.isSymLink(file)

        fun getContentFsBashDetails(dir: File) = implementation.getContentFsBashDetails(dir)
        fun lnS(symbolic: File, target: String) = implementation.lnS(symbolic, target)
        fun stuffModifiedAfter(directory: File, pruneDir: File, timeStamp: Long): AutoKlausIterator<File> = implementation.stuffModifiedAfter(directory, pruneDir, timeStamp)
        fun stuffModifiedAfter(referenceFile: File, directory: File, pruneDir: File): List<File> = implementation.stuffModifiedAfter(referenceFile, directory, pruneDir)
        fun setCreationDate(target: File, created: Long) = implementation.setCreationDate(target = target, created = created)
        fun canFindByTime(): Boolean = implementation.canFindByTime()
    }

    open fun isSymLink(f: File): Boolean = Files.isSymbolicLink(f.toPath())

    abstract fun lnS(file: File, target: String)
    open fun setBinPath(binPath: String) {
        // Override if needed
    }

    open fun setCreationDate(target: File, created: Long) {
        val path = target.toPath()
        Files.setAttribute(path, "creationTime", FileTime.fromMillis(created))
    }

    // todo this can be replaces by calling constructor
    @Deprecated(level = DeprecationLevel.WARNING, message = "should be replaced by calling constructor directly", replaceWith = ReplaceWith("FsBashDetails(created, modified, iNode, symLink, symLinkTarget, name)", "de.mel.filesync.bash.FsBashDetails"))
    open fun createFsBashDetails(created: Long?, modified: Long, iNode: Long, symLink: Boolean, symLinkTarget: String?, name: String): FsBashDetails {
        return FsBashDetails(created, modified, iNode, symLink, symLinkTarget, name)
    }

    abstract fun getContentFsBashDetails(directory: File): MutableMap<String, FsBashDetails>
    @Throws(exceptionClasses = [IOException::class])
    abstract fun rmRf(directory: File)

    //todo replace return type with AutoKlausIterator
    @Throws(exceptionClasses = [IOException::class, de.mel.filesync.bash.BashToolsException::class])
    abstract fun stuffModifiedAfter(referenceFile: File, directory: File, pruneDir: File): List<File>

    @Throws(exceptionClasses = [IOException::class])
    abstract fun find(directory: File, pruneDir: File): AutoKlausIterator<File>

    @Throws(exceptionClasses = [IOException::class, java.lang.InterruptedException::class])
    abstract fun stuffModifiedAfter(directory: File, pruneDir: File, timeStamp: Long): AutoKlausIterator<File>

    /**
     * @return true if [stuffModifiedAfter] with a time stamp is done by `find` (which is fast and also finds what only changed in ctime).
     * Then the watcher needs no time reference files. Platforms where `find` cannot do this say false.
     */
    open fun canFindByTime(): Boolean = false
}
