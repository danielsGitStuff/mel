package de.mel.android.file

import android.annotation.TargetApi
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import de.mel.Lok
import de.mel.android.Tools
import de.mel.android.file.SAFAccessor.SAFException
import de.mel.auth.file.AbstractFile
import de.mel.auth.file.AbstractFileWriter
import de.mel.auth.file.IFile
import de.mel.auth.file.StandardFileWriter
import de.mel.auth.tools.N
import de.mel.auth.tools.N.arr
import java.io.*
import java.util.*

/**
 * File replacement for Android KitKat to Pie
 */
class AndroidFile : AbstractFile<AndroidFile> {
    private var file: File
    private var isExternal = false
    private var internalCache: DocFileCache? = null
    private var externalCache: DocFileCache? = null

    companion object{
        fun createRelativeFilePathParts(storagePath: String, file: File): Array<String> {
            val path: String? = file.absolutePath
            // if rootPath is null, there is no external sd card.
            // if it isn't the share still might be on the internal "sd card"
            // todo test, because it is untested cause I go to bed now

            val stripped: String?
            if (!path!!.startsWith(storagePath)) Lok.error("INVALID PATH! tried to find [$path] in [$storagePath]")
            // +1 to get rid of the leading slash


            var offset = 0
            if (!path.endsWith("/")) offset = 1
            if (path.length == storagePath.length) return emptyArray()
            stripped = path.substring(storagePath.length + offset)
            return stripped.split(File.separator).toTypedArray()
        }
    }

    constructor(path: String) {
        file = File(path)
        name = file.name
        init()
    }

    constructor(file: File) {
        this.file = file
        init()
    }

    constructor(parent: AndroidFile, name: String) {
        parentFile = parent
        file = File(parent.absolutePath + File.separator + name)
        init()
    }

    constructor(originalFile: AndroidFile) {
        file = File(originalFile.absolutePath!!)
        init()
    }

    private fun init() {
        isExternal = SAFAccessor.isExternalFile(this)
    }

    override val separator: String?
        get() = File.separator


    override fun hasSubContent(subFile: AbstractFile<*>): Boolean {
        return subFile.absolutePath.startsWith(file!!.absolutePath) ?: false
    }


    override fun exists(): Boolean {
        return file!!.exists()
    }


//    @Override
//    public boolean move(JFile target) {
//        try {
//            Intent copyIntent = new Intent(Tools.getApplicationContext(), CopyService.class);
//            copyIntent.putExtra(CopyService.SRC_PATH, file.getAbsolutePath());
//            copyIntent.putExtra(CopyService.TRGT_PATH, target.file.getAbsolutePath());
//            copyIntent.putExtra(CopyService.MOVE, true);
//            Tools.getApplicationContext().startService(copyIntent);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return false;
//    }


    override fun equals(other: Any?): Boolean {
        if (other != null && other is AndroidFile) {
            val jFile = other as AndroidFile?
            return jFile!!.file == file
        }
        return false
    }

    override fun length(): Long {
        return file!!.length()
    }

    override fun listFiles(): Array<AndroidFile> {
        return list(true)
    }

    override fun listDirectories(): Array<AndroidFile> {
        return list(false)
    }

    override fun delete(): Boolean {
        if (requiresSAF()) {
            try {
                val documentFile = getDocFile()
                if (documentFile != null) return documentFile.delete()
            } catch (e: SAFException) {
                e.printStackTrace()
            }
        } else {
            return file!!.delete()
        }
        return true
    }


    @TargetApi(VERSION_CODES.KITKAT)
    private fun mkdir(): Boolean {
        try {
            if (requiresSAF()) {
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
            } else {
                File(absolutePath).mkdir()
            }
        } catch (e: SAFException) {
            e.printStackTrace()
        }
        return false
    }

    override fun mkdirs(): Boolean {
        if (requiresSAF()) {
            if (parentFile != null) if (!parentFile!!.exists()) {
                val made = parentFile!!.mkdirs()
                if (!made) return false
            }
            return mkdir()
        } else {
            return file!!.mkdirs()
        }
    }

    @Throws(FileNotFoundException::class)
    override fun inputStream(): InputStream? {
        if (VERSION.SDK_INT < Build.VERSION_CODES.Q)
            return FileInputStream(file)
        val contentResolver: ContentResolver = (configuration as AndroidFileConfiguration).context.contentResolver
        return contentResolver.openInputStream(getDocFile()!!.uri)
    }

    @Throws(IOException::class)
    override fun writer(): AbstractFileWriter? {
        try {
            return if (requiresSAF()) {
                var documentFile = getDocFile()
                if (documentFile == null) {
                    val parent = createParentDocFile()
                    documentFile = parent!!.createFile(SAFAccessor.MIME_GENERIC, file!!.name)!!
                }
                StandardFileWriter(Tools.getApplicationContext().contentResolver.openOutputStream(documentFile.uri) as FileOutputStream)
            } else if (VERSION.SDK_INT > VERSION_CODES.P) {
                AndroidFileWriter(this)
            } else {
                StandardFileWriter(FileOutputStream(file))
            }
        } catch (e: SAFException) {
            e.printStackTrace()
        }
        return null
    }

    override fun lastModified(): Long? {
        return file!!.lastModified()
    }

    /**
     * Checks whether or not you have to employ the Storage Access Framework to write to that location.
     * Check only works until Android Pie.
     *
     * @return
     */
    private fun requiresSAF(): Boolean {
        val internalDataPath: String = Environment.getDataDirectory().absolutePath

        // file is in data directory
        if (absolutePath.startsWith(internalDataPath))
            return false

        //android 10 onwards check
        if (VERSION.SDK_INT > VERSION_CODES.P && !absolutePath.startsWith(AndroidFileConfiguration.getDataDir().absolutePath))
            return true

        // no external sd card available
        if (!SAFAccessor.hasExternalSdCard()) return false

        // file is not on external sd card
        if (file!!.absolutePath.startsWith(internalDataPath!!)) return false


        // SAF is only available from kitkat onwards
        return if (!file!!.absolutePath.startsWith(SAFAccessor.getExternalSDPath())) false else VERSION.SDK_INT > VERSION_CODES.KITKAT
    }



    /**
     * @return the path of the storage the file is stored on
     */
    private val storagePath: String
        private get() {
            val storagePath: String?
            storagePath = if (isExternal) {
                SAFAccessor.getExternalSDPath()
            } else {
                Environment.getExternalStorageDirectory().absolutePath
            }
            return storagePath
        }

    @Throws(SAFException::class)
    fun createParentDocFile(): DocumentFile? {
        var parts = createRelativeFilePathParts(storagePath, file!!)
        parts = Arrays.copyOf(parts, parts!!.size - 1)
        return if (isExternal) getExternalDoc(parts) else getInternalDoc(parts)
    }

    @Throws(SAFException::class)
    fun getDocFile(): DocumentFile? {
        val storagePath = storagePath
        val parts: Array<String>
        parts = createRelativeFilePathParts(storagePath, file!!)
        return if (!isExternal) {
            getInternalDoc(parts)
        } else getExternalDoc(parts)
    }

    @Throws(SAFException::class)
    private fun getInternalDoc(parts: Array<String>): DocumentFile? {
        if (internalCache == null) internalCache = DocFileCache(SAFAccessor.getInternalRootDocFile(), 50)
        return internalCache!!.findDoc(parts)
    }

    @Throws(SAFException::class)
    private fun getExternalDoc(parts: Array<String>): DocumentFile? {
        if (externalCache == null) externalCache = DocFileCache(SAFAccessor.getExternalRootDocFile(), 50)
        return externalCache!!.findDoc(parts)
    }

    @Throws(IOException::class)
    override fun createNewFile(): Boolean {
        if (requiresSAF()) {
            try {
                val folderDoc = createParentDocFile()
                val found = folderDoc!!.findFile(file!!.name)
                if (found != null) {
                    return false
                }
                val created = folderDoc.createFile(SAFAccessor.MIME_GENERIC, file!!.name)
                return if (created != null) {
                    true
                } else
                    false
            } catch (e: SAFException) {
                e.printStackTrace()
            }
        } else if (VERSION.SDK_INT > VERSION_CODES.P && !absolutePath.startsWith(AndroidFileConfiguration.getDataDir().absolutePath)) {
            val parentDoc = (parentFile as AndroidFile).getDocFile()
            val contentResolver: ContentResolver = (configuration as AndroidFileConfiguration).context.contentResolver
            val uri = DocumentsContract.createDocument(contentResolver, parentDoc!!.uri, SAFAccessor.MIME_GENERIC, name)
            return uri != null
        } else {
            return file!!.createNewFile()
        }
        return false
    }

    private fun listImplPie(filterOutDirs: Boolean?): Array<AndroidFile> {
        val directory = File(file!!.absolutePath)

        // File not found error


        if (!directory.exists()) {
            return emptyArray()
        }
        if (!directory.canRead()) {
            return emptyArray()
        }
        val listFiles: Array<File> = (if (filterOutDirs != null) if (filterOutDirs) directory.listFiles { obj: File -> obj.isFile } else directory.listFiles { obj: File -> obj.isDirectory } else directory.listFiles())
                ?: return emptyArray()


        // Check Error in reading the directory (java.io.File do not allow any details about the error...).


        Arrays.sort(listFiles) { o1: File?, o2: File? -> o1!!.name.compareTo(o2!!.name) }
        return arr.cast(listFiles, N.converter(AndroidFile::class.java) { file: File -> AndroidFile(file) })
    }

    private fun list(filterOutDirs: Boolean?): Array<AndroidFile> {
        return if (VERSION.SDK_INT >= VERSION_CODES.Q) listImplQ(filterOutDirs) else listImplPie(filterOutDirs)
    }

    /**
     * @param filterOutDirs if null: unfiltered, if true: no dirs, if false: no files
     * @return
     */
    @RequiresApi(VERSION_CODES.Q)
    private fun listImplQ(filterOutDirs: Boolean?): Array<AndroidFile> {
        /**
         * Here we employ database queries to find the content. This reduced the time listing directory contents from 7500ms to 250ms (filtering etc included)
         * compared to using getDocFile().listFiles().
         */
        val directory = File(file!!.absolutePath)
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
            return fileList.map { AndroidFile(this, it) }.toTypedArray()
        } catch (e: SAFException) {
            e.printStackTrace()
        }
        return emptyArray()
    }

    override fun listContent(): Array<AndroidFile>? {
        return list(null)
    }

    override val absolutePath: String
        get() = file!!.absolutePath
    override val isFile: Boolean
        get() = file!!.isFile
    override val isDirectory: Boolean
        get() = file!!.isDirectory

    override val freeSpace: Long?
        get() = file!!.freeSpace
    override val usableSpace: Long?
        get() = file!!.usableSpace
    override val path: String
        get() = file!!.path

    override fun canRead(): Boolean = file!!.canRead()

    override val canonicalPath: String?
        get() = file!!.canonicalPath
    override var parentFile: AndroidFile?
        get() = if (file != null && file!!.parentFile != null) AbstractFile.instance(file!!.parentFile!!) as AndroidFile else null
        set(value) {}
}