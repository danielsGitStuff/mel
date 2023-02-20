package de.mel.auth.file

import de.mel.core.serialize.SerializableEntity
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlin.properties.ReadOnlyProperty

interface IFile :SerializableEntity{
    val separator: String?
    val absolutePath: String
    val isFile: Boolean
    //    public abstract boolean move(T target);
    val isDirectory: Boolean
    //    var parentFile: IFile
    val freeSpace: Long?
    val usableSpace: Long?
    val path: String


    fun getParentFile(): IFile?

    /**
     * @param subFile
     * @return true if subFile is located in a subfolder of this instance.
     */
    fun hasSubContent(subFile: IFile): Boolean

    fun canRead(): Boolean
    fun exists(): Boolean
    fun length(): Long
    fun listFiles(): Array<out IFile>
    fun listDirectories(): Array<out IFile>
    fun delete(): Boolean
    fun mkdirs(): Boolean
    @Throws(FileNotFoundException::class)
    fun inputStream(): InputStream?

    fun getName(): String

    @Throws(IOException::class)
    fun writer(): AbstractFileWriter?

    fun lastModified(): Long?
    @Throws(IOException::class)
    fun createNewFile(): Boolean

//    @Throws(IOException::class)
//    fun getUsableSpace(): Long

    fun listContent(): Array<out IFile>?

    @get:Throws(IOException::class)
    val canonicalPath: String?
}