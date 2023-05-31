package de.mel.auth.file

import de.mel.Lok
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException

/**
 * this is the default [File] wrapper for everything that is not recent android
 */
class StandardFile : AbstractFile<StandardFile> {
    lateinit var file: File
        private set

    constructor() {}

    override var path: String
        get() = file.path
        set(path) {
            file = File(path)
        }

    override fun getParentFile(): IFile? = StandardFile(file.parentFile)
    override fun hasSubContent(subFile: IFile): Boolean = subFile.absolutePath.startsWith(absolutePath)

    override fun canRead(): Boolean = file.canRead()

    constructor(parent: StandardFile, name: String) {
        file = File(parent.file, name)
    }

    constructor(originalFile: StandardFile) {
        file = File(originalFile.file.absolutePath)
    }

    override fun toString(): String {
        return file.toString()
    }

    constructor(parent: File, name: String) {
        file = File(parent, name)
    }

    constructor(file: File) {
        this.file = file
    }

    constructor(path: String) : this(File(path)) {}

    override val absolutePath: String
        get() = file.absolutePath

    override fun exists(): Boolean {
        return file.exists()
    }

    override val isFile: Boolean
        get() = file.isFile


    //    @Override
//    public boolean move(FFile target) {
//        return file.renameTo(new File(target.getAbsolutePath()));
//    }
    override val isDirectory: Boolean
        get() = file.isDirectory

    override fun length(): Long {
        return file.length()
    }

    override fun listFiles(): Array<StandardFile> = file.listFiles { f -> f.isFile }?.map { StandardFile(it) }?.toTypedArray()
            ?: emptyArray()

    override fun listDirectories(): Array<StandardFile> = file.listFiles { f -> f.isDirectory }?.map { StandardFile(it) }?.toTypedArray()
            ?: emptyArray()


    override fun delete(): Boolean {
        return file.delete()
    }

    override fun mkdirs(): Boolean {
        // todo debug
        if (file.name == "i0")
            Lok.debug()
        return file.mkdirs()
    }

    @Throws(FileNotFoundException::class)
    override fun inputStream(): FileInputStream? {
        return FileInputStream(file)
    }

    override fun getName(): String = file.name

    @Throws(FileNotFoundException::class)
    override fun writer(): AbstractFileWriter? {
        return StandardFileWriter(file)
    }

    override val separator: String
        get() = File.separator

    fun hasSubContent(subFile: StandardFile?): Boolean {
        return subFile != null && subFile.absolutePath.startsWith(file!!.absolutePath)
    }

    @get:Throws(IOException::class)
    override val canonicalPath: String
        get() = file!!.canonicalPath

    override val freeSpace: Long
        get() = file.freeSpace

    override val usableSpace: Long
        get() = file.usableSpace

    override fun lastModified(): Long? {
        return file.lastModified()
    }

    @Throws(IOException::class)
    override fun createNewFile(): Boolean {
        return file.createNewFile()
    }


    override fun listContent(): Array<StandardFile> {
        return file.listFiles()?.map { StandardFile(it) }?.toTypedArray() ?: emptyArray()
    }
}