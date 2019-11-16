package de.mel.filesync.bash

import de.mel.auth.file.AbstractFile
import de.mel.auth.file.IFile
import de.mel.auth.file.StandardFile
import de.mel.filesync.bash.BufferedIterator.BufferedFileIterator
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime


abstract class BashTools<A : IFile> {
    abstract fun getFsBashDetails(file: A): FsBashDetails?

    companion object {
        var binPath: String? = null
        val isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows")
        lateinit var implementation: BashTools<AbstractFile<*>>
        fun <F : AbstractFile<Any>> getFsBashDetails(file: F): FsBashDetails? = implementation.getFsBashDetails(file)

        fun init() {
            if (implementation == null) if (BashTools.isWindows) {
                implementation = BashToolsWindows() as BashTools<AbstractFile<*>>
            } else {
                implementation = BashToolsUnix() as BashTools<AbstractFile<*>>
            }
        }

        fun inputStreamToFileIterator(inputStream: InputStream): AutoKlausIterator<AbstractFile<*>> {
            return BufferedFileIterator(InputStreamReader(inputStream))
        }

        fun rmRf(file: AbstractFile<*>) = implementation.rmRf(file)
        fun find(root: AbstractFile<*>, pruneDir: AbstractFile<*>): AutoKlausIterator<AbstractFile<*>> = implementation.find(root, pruneDir)
        fun isSymLink(file: AbstractFile<*>) = implementation.isSymLink(file)

        fun getContentFsBashDetails(dir: AbstractFile<*>) = implementation.getContentFsBashDetails(dir)
        fun lnS(symbolic: AbstractFile<*>, target: String) = implementation.lnS(symbolic, target)
        fun stuffModifiedAfter(directory: IFile, pruneDir: IFile, timeStamp: Long): AutoKlausIterator<IFile> = implementation.stuffModifiedAfter(directory, pruneDir, timeStamp)
        fun setCreationDate(target: AbstractFile<*>, created: Long) = implementation.setCreationDate(target = target, created = created)
    }

    open fun isSymLink(f: A): Boolean = Files.isSymbolicLink(Paths.get(File(f.absolutePath).toURI()))

    abstract fun lnS(file: A, target: String)
    open fun setBinPath(binPath: String) {
        // Override if needed
    }

    open fun setCreationDate(target: A, created: Long) {
        val path = Paths.get(File(target.absolutePath).toURI())
        Files.setAttribute(path, "creationTime", FileTime.fromMillis(created))
    }

    // todo this can be replaces by calling constructor
    @Deprecated(level = DeprecationLevel.WARNING, message = "should be replaced by calling constructor directly", replaceWith = ReplaceWith("FsBashDetails(created, modified, iNode, symLink, symLinkTarget, name)", "de.mel.filesync.bash.FsBashDetails"))
    open fun createFsBashDetails(created: Long?, modified: Long, iNode: Long, symLink: Boolean, symLinkTarget: String?, name: String): FsBashDetails {
        return FsBashDetails(created, modified, iNode, symLink, symLinkTarget, name)
    }

    abstract fun getContentFsBashDetails(directory: A): MutableMap<String, FsBashDetails>
    @Throws(exceptionClasses = [IOException::class])
    abstract fun rmRf(directory: A)

    @Throws(exceptionClasses = [IOException::class, de.mel.filesync.bash.BashToolsException::class])
    abstract fun stuffModifiedAfter(referenceFile: A, directory: A, pruneDir: A): List<A>

    @Throws(exceptionClasses = [IOException::class])
    abstract fun find(directory: A, pruneDir: A): AutoKlausIterator<A>

    @Throws(exceptionClasses = [IOException::class, java.lang.InterruptedException::class])
    abstract fun stuffModifiedAfter(directory: A, pruneDir: A, timeStamp: Long): AutoKlausIterator<A>
}