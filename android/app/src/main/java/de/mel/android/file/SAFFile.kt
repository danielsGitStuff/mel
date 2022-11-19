package de.mel.android.file

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import de.mel.Lok
import de.mel.auth.file.AbstractFile
import de.mel.auth.file.AbstractFileWriter
import de.mel.auth.file.IFile
import de.mel.filesync.bash.BashToolsException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.*

/**
 * File replacement for Android 10+
 */
@RequiresApi(Build.VERSION_CODES.Q)
class SAFFile : AbstractFile<SAFFile> {


    fun getUri(): Uri {
        if (uriField == null)
            uriField = getDocFile().uri
        if (uriField == null)
            throw FileNotFoundException("could not get a URI for ${file.absolutePath}")
        return uriField!!
    }

    private var uriField: Uri? = null
    private var isExternal = false
    private lateinit var file: File
    val fileConfig: SAFFileConfiguration

    constructor(fileConfig: SAFFileConfiguration, path: String) {
        file = File(path)
        this.fileConfig = fileConfig
        init()
    }

    constructor(fileConfig: SAFFileConfiguration, file: File) {
        this.fileConfig = fileConfig
        this.file = file
        init()
    }

    constructor(parent: SAFFile, name: String) {
        this.fileConfig = parent.fileConfig
        file = File(parent.absolutePath, name)
        init()
    }

    constructor(originalFile: SAFFile) {
        this.fileConfig = originalFile.fileConfig
        file = File(originalFile.absolutePath)
        init()
    }

    private fun init() {
        isExternal = SAFAccessor.isExternalFile(this)
    }

    private fun query() {

    }

    @Throws(SAFAccessor.SAFException::class)
    fun getDocFile(): DocumentFile {
        val storagePath = storagePath
        val parts: Array<String>
        parts = AndroidFile.createRelativeFilePathParts(storagePath, file!!)
        return if (!isExternal) {
            fileConfig.getInternalDoc(parts)
        } else fileConfig.getExternalDoc(parts)
    }

    /**
     * @return the path of the storage the file is stored on
     */
    val storagePath: String
        get() {
            return if (isExternal) {
                SAFAccessor.getExternalSDPath()
            } else {
                Environment.getExternalStorageDirectory().absolutePath
            }
        }


    private fun listImplPie(filterOutDirs: Boolean?): Array<SAFFile>? {
        val directory = file

        // File not found error


        if (!directory.exists()) {
            return emptyArray()
        }
        if (!directory.canRead()) {
            return emptyArray()
        }
        val listFiles: Array<File> = (if (filterOutDirs != null) if (filterOutDirs) directory.listFiles { obj: File -> obj.isFile } else directory.listFiles { obj: File -> obj.isDirectory } else directory.listFiles())
                ?: emptyArray()


        // Check Error in reading the directory (java.io.File do not allow any details about the error...).


        Arrays.sort(listFiles) { o1: File?, o2: File? -> o1!!.name.compareTo(o2!!.name) }
        return listFiles.map { SAFFile(fileConfig, it) }.toTypedArray()
    }

    private fun list(filterOutDirs: Boolean?): Array<SAFFile>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listImplQ(filterOutDirs) else listImplPie(filterOutDirs)
    }


    /**
     * @param filterOutDirs if null: unfiltered, if true: no dirs, if false: no files
     * @return
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun listImplQ(filterOutDirs: Boolean?): Array<SAFFile>? {
        /**
         * Here we employ database queries to find the content. This reduced the time listing directory contents from 7500ms to 250ms (filtering etc included)
         * compared to using getDocFile().listFiles().
         */
        val directory = File(absolutePath)
        if (!directory.exists()) {
            return emptyArray()
        }
        try {
            val thisDoc = getDocFile()!!
            val uri: Uri = DocumentsContract.buildChildDocumentsUriUsingTree(thisDoc.uri, DocumentsContract.getDocumentId(thisDoc.uri))
            val contentResolver: ContentResolver = (configuration as AndroidFileConfiguration).context.contentResolver
// this code maybe useful when someone eventually found out how that stupid query() thing works, see comment below
//            var dirFilterSelection: String? = null
//            var dirFilterArgs: Array<String>? = null
//            if (filterOutDirs != null) {
//                dirFilterArgs = arrayOf(DocumentsContract.Document.MIME_TYPE_DIR)
//                dirFilterSelection = if (filterOutDirs) {
//                    "${DocumentsContract.Document.COLUMN_MIME_TYPE}!=?"
//                } else {
//                    "${DocumentsContract.Document.COLUMN_MIME_TYPE}=?"
//                }
//            }
            val fileList = mutableListOf<String>()
            /*
             * SAF is a cunt. You could plug in a sort order, selection and args just to see it being ignored. It does not crash though.
             * "I do things wrong but look how fast I am!"
             */
//            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), dirFilterSelection, dirFilterArgs, null, null)?.use {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null, null)?.use {
                it.moveToFirst()
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val mimeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (!it.isAfterLast) {
                    val name = it.getString(nameIndex)
                    val mime = it.getString(mimeIndex)
                    if (filterOutDirs == null) {
                        fileList += name
                    } else {
                        if (filterOutDirs xor mime.equals(DocumentsContract.Document.MIME_TYPE_DIR))
                            fileList += name
                    }

//                    Lok.debug(name)
                    it.moveToNext()
                }
            }
            fileList.sort()
            return fileList.map { SAFFile(this, it) }.toTypedArray()
        } catch (e: SAFAccessor.SAFException) {
            e.printStackTrace()
        }
        return emptyArray()
    }

    override fun exists(): Boolean = getDocFile() != null

    override fun length(): Long {
        fileConfig.context.contentResolver.query(getUri(), arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)
                ?.use {
                    it.moveToFirst()
                    return it.getLong(it.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE))
                }
        throw IOException("file ${file.absolutePath} is either a directory or does not exist")
    }

    override fun listFiles(): Array<IFile> = listAndFilter(true)

    override fun listDirectories(): Array<IFile> = listAndFilter(false)

    private fun listAndFilter(filterOutDirs: Boolean?): Array<IFile> {
        val directory = File(file!!.absolutePath)
        if (!directory.exists()) {
            return emptyArray()
        }
        try {
            val thisDoc = getDocFile()!!
            val uri: Uri = DocumentsContract.buildChildDocumentsUriUsingTree(thisDoc.uri, DocumentsContract.getDocumentId(thisDoc.uri))
            val contentResolver: ContentResolver = fileConfig.context.contentResolver
// this code maybe useful when someone eventually found out how that stupid query() thing works, see comment below
//            var dirFilterSelection: String? = null
//            var dirFilterArgs: Array<String>? = null
//            if (filterOutDirs != null) {
//                dirFilterArgs = arrayOf(DocumentsContract.Document.MIME_TYPE_DIR)
//                dirFilterSelection = if (filterOutDirs) {
//                    "${DocumentsContract.Document.COLUMN_MIME_TYPE}!=?"
//                } else {
//                    "${DocumentsContract.Document.COLUMN_MIME_TYPE}=?"
//                }
//            }
            val fileList = mutableListOf<String>()
            /*
             * SAF is a cunt. You could plug in a sort order, selection and args just to see it being ignored. It does not crash though.
             * "I do things wrong but look how fast I am!"
             */
//            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), dirFilterSelection, dirFilterArgs, null, null)?.use {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null, null)?.use {
                it.moveToFirst()
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val mimeIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (!it.isAfterLast) {
                    val name = it.getString(nameIndex)
                    val mime = it.getString(mimeIndex)
                    if (filterOutDirs == null) {
                        fileList += name
                    } else {
                        if (filterOutDirs xor mime.equals(DocumentsContract.Document.MIME_TYPE_DIR))
                            fileList += name
                    }

//                    Lok.debug(name)
                    it.moveToNext()
                }
            }
            fileList.sort()
            return fileList.map { SAFFile(this, it) }.toTypedArray()
        } catch (e: SAFAccessor.SAFException) {
            e.printStackTrace()
        }
        return emptyArray()
    }

    override fun delete(): Boolean = getDocFile()!!.delete()


    override fun mkdirs(): Boolean {
        val parentFile = getParentFile()
        if (parentFile != null) if (!parentFile!!.exists()) {
            val made = parentFile!!.mkdirs()
            if (!made) return false
        }
        return mkdir()
    }

    private fun mkdir(): Boolean {
        val folderDoc = createParentDocFile()
        val name: String? = file!!.name
        val found = folderDoc!!.findFile(name!!)
        if (found != null && found.isFile) {
            found.delete()
        }
        if (found != null) {
            return false
        }
        val created = folderDoc.createDirectory(name)
        return created != null && created.exists()
    }

    @Throws(SAFAccessor.SAFException::class)
    fun createParentDocFile(): DocumentFile? {
        var parts = AndroidFile.createRelativeFilePathParts(storagePath, file)
        parts = Arrays.copyOf(parts, parts.size - 1)
        return if (isExternal) fileConfig.getExternalDoc(parts) else fileConfig.getInternalDoc(parts)
    }


    override fun inputStream(): InputStream? = fileConfig.context.contentResolver.openInputStream(getDocFile()!!.uri)

    override fun getName(): String = file.name

    override fun writer(): AbstractFileWriter? = SAFFileWriter(this)

    override fun lastModified(): Long? {
        if (!exists())
            return null
        fileConfig.context.contentResolver.query(getUri(), arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)
                ?.use {
                    it.moveToFirst()
                    return it.getLong(it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
                }
        throw FileNotFoundException("$absolutePath not found")
    }

    override fun createNewFile(): Boolean = DocumentsContract.createDocument(fileConfig.context.contentResolver, getParentFile()!!.getUri(), SAFAccessor.MIME_GENERIC, file.name) != null


    override fun listContent(): Array<out IFile>? = listAndFilter(null)

    override val separator: String?
        get() = "%2F"
    override val absolutePath: String by lazy { file.absolutePath }
    override val isFile: Boolean by lazy { file.isFile }
    override val isDirectory: Boolean by lazy { file.isDirectory }
    override val freeSpace: Long? by lazy { file.freeSpace }
    override val usableSpace: Long? by lazy { file.usableSpace }
    override val path: String by lazy { file.path }

    override fun getParentFile(): SAFFile? = if (file.parentFile != null) instance(file!!.parentFile!!) as SAFFile else null

    override fun hasSubContent(subFile: IFile): Boolean = subFile.absolutePath.startsWith(absolutePath)

    override fun canRead(): Boolean {
        fileConfig.context.contentResolver.openInputStream(getUri())?.use {
            it.close()
            return true
        }
        return false
    }

    fun getChildrenUri(): Uri = DocumentsContract.buildChildDocumentsUriUsingTree(getUri(), DocumentsContract.getDocumentId(getUri()))


    override val canonicalPath: String? by lazy { file.canonicalPath }

    override fun toString(): String = file.absolutePath
}