package de.mel.auth.file

import de.mel.Lok
import de.mel.auth.file.AbstractFile.Configuration
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * Think of this as an abstraction of [java.io.File]. It is necessary cause Android 7+ restricts access to external storages via [java.io.File].
 * Before using [AbstractFile], call configure() and hand over a [Configuration]. This determines the implementation you want to use.
 * [DefaultFileConfiguration] uses [StandardFile] which wraps [File].
 */
abstract class AbstractFile<out T : IFile> :IFile {
    /**
     * @param subFile
     * @return true if subFile is located in a subfolder of this instance.
     */
    override fun hasSubContent(subFile: AbstractFile<*>): Boolean = subFile.absolutePath.startsWith(absolutePath)



    /**
     * creates common instances of [AbstractFile]s
     */
    abstract class Configuration {
        abstract fun instance(path: String): AbstractFile<IFile>
        abstract fun separator(): String
        abstract fun instance(file: File): AbstractFile<IFile>
        abstract fun instance(parent: AbstractFile<IFile>, name: String): AbstractFile<IFile>
        abstract fun instance(originalFile: AbstractFile<IFile>): AbstractFile<IFile>
    }

    companion object {
        var configuration: Configuration? = null
            private set

        @JvmStatic
        fun instance(file: File): AbstractFile<IFile> {
            return configuration!!.instance(file)
        }

        @JvmStatic
        fun instance(originalFile: AbstractFile<IFile>): AbstractFile<IFile> {
            return configuration!!.instance(originalFile)
        }

        @JvmStatic
        fun configure(configuration: Configuration) {
            if (Companion.configuration != null) {
                Lok.error("AFile implementation has already been set!")
                return
            } else {
                Companion.configuration = configuration
            }
        }

        /**
         * It just creates some sort of root element.
         *
         * @param path
         * @return
         */
        @JvmStatic
        fun instance(path: String): AbstractFile<IFile> {
            if (configuration == null) Lok.error(AbstractFile::class.java.simpleName + ". NOT INITIALIZED! Call configure() before!")
            return configuration!!.instance(path)
        }

        @JvmStatic
        fun instance(parent: AbstractFile<IFile>, name: String): AbstractFile<IFile> {
            return configuration!!.instance(parent, name)
        }

        @JvmStatic
        fun separator(): String {
            return configuration!!.separator()
        }
    }
}