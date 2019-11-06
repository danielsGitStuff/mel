package de.mel.android.file

import android.annotation.TargetApi
import android.content.ContentResolver
import android.net.Uri
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
import de.mel.auth.file.AFile
import de.mel.auth.tools.N
import de.mel.auth.tools.N.arr
import java.io.*
import java.util.*

class JFile : AFile<JFile> {
    private var file: File?
    private var parentFile: JFile? = null
    private var isExternal = false
    private var internalCache: DocFileCache? = null
    private var externalCache: DocFileCache? = null

    constructor(path: String) {
        file = File(path)
        init()
    }

    constructor(file: File) {
        this.file = file
        init()
    }

    constructor(parent: JFile, name: String) {
        parentFile = parent
        file = File(parent.absolutePath + File.separator + name)
        init()
    }

    constructor(originalFile: JFile) {
        file = File(originalFile.absolutePath!!)
        init()
    }

    private fun init() {
        isExternal = SAFAccessor.isExternalFile(this)
    }

    override fun getSeparator(): String? {
        return File.separator
    }

    override fun hasSubContent(subFile: JFile?): Boolean {
        return if (subFile != null) subFile.file!!.absolutePath.startsWith(file!!.absolutePath) else false
    }

    @Throws(IOException::class)
    override fun getCanonicalPath(): String {
        return file!!.canonicalPath
    }

    override fun getName(): String {
        return file!!.name
    }

    override fun getAbsolutePath(): String {
        return file!!.absolutePath
    }

    override fun exists(): Boolean {
        return file!!.exists()
    }

    override fun isFile(): Boolean {
        return file!!.isFile
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
        if (other != null && other is JFile) {
            val jFile = other as JFile?
            return jFile!!.file == file
        }
        return false
    }

    override fun isDirectory(): Boolean {
        return file!!.isDirectory
    }

    override fun length(): Long? {
        return file!!.length()
    }

    override fun listFiles(): Array<JFile>? {
        return list(true)
    }

    override fun listDirectories(): Array<JFile>? {
        return list(false)
    }

    override fun delete(): Boolean {
        if (requiresSAF()) {
            try {
                val documentFile = createDocFile()
                if (documentFile != null) return documentFile.delete()
            } catch (e: SAFException) {
                e.printStackTrace()
            }
        } else {
            return file!!.delete()
        }
        return true
    }

    override fun getParentFile(): JFile {
        return JFile(file!!.parentFile!!)
    }

    @TargetApi(VERSION_CODES.KITKAT)
    private fun mkdir(): Boolean {
        try {
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
    override fun inputStream(): FileInputStream? {
//        try {
//            InputStream stream = getFileEditor().getInputStream();
//            return (FileInputStream) stream;
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        return FileInputStream(file)
    }

    @Throws(IOException::class)
    override fun outputStream(): FileOutputStream? {
        try {
            return if (requiresSAF()) {
                var documentFile = createDocFile()
                if (documentFile == null) {
                    val parent = createParentDocFile()
                    documentFile = parent!!.createFile(SAFAccessor.MIME_GENERIC, file!!.name)!!
                }
                Tools.getApplicationContext().contentResolver.openOutputStream(documentFile!!.uri) as FileOutputStream?
            } else {
                FileOutputStream(file)
            }
        } catch (e: SAFException) {
            e.printStackTrace()
        }
        return null
    }

    override fun getFreeSpace(): Long? {
//        File ioFile = new File(file.getUri().getEncodedPath());
//        if (ioFile.exists())
//            return ioFile.getFreeSpace();

        return file!!.freeSpace
    }

    override fun getUsableSpace(): Long? {
        return file!!.usableSpace
    }

    override fun lastModified(): Long? {
        return file!!.lastModified()
    }

    /**
     * Checks whether or not you have to employ the Storage Access Framework to write to that location.
     *
     * @return
     */
    private fun requiresSAF(): Boolean {
        val internalDataPath: String? = Environment.getDataDirectory().absolutePath
        // no external sd card available


        if (!SAFAccessor.hasExternalSdCard()) return false
        // file is in data directory


        if (file!!.absolutePath.startsWith(internalDataPath!!)) return false
        // file is not on external sd card


        return if (!file!!.absolutePath.startsWith(SAFAccessor.getExternalSDPath())) false else VERSION.SDK_INT > VERSION_CODES.KITKAT
        // SAF is only available from kitkat onwards


    }

    private fun createRelativeFilePathParts(storagePath: String, file: File): Array<String> {
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
        return if (isExternal) createExternalDoc(parts) else createInternalDoc(parts)
    }

    @Throws(SAFException::class)
    fun createDocFile(): DocumentFile? {
        val storagePath = storagePath
        val parts: Array<String>
        parts = createRelativeFilePathParts(storagePath, file!!)
        return if (!isExternal) {
            createInternalDoc(parts)
        } else createExternalDoc(parts)
    }

    @Throws(SAFException::class)
    private fun createInternalDoc(parts: Array<String>): DocumentFile? {
        if (internalCache == null) internalCache = DocFileCache(SAFAccessor.getInternalRootDocFile(), 50)
        return internalCache!!.findDoc(parts)
    }

    @Throws(SAFException::class)
    private fun createExternalDoc(parts: Array<String>): DocumentFile? {
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
                } else false
            } catch (e: SAFException) {
                e.printStackTrace()
            }
        } else {
            return file!!.createNewFile()
        }
        return false
    }

    private fun listImplPie(filterOutDirs: Boolean?): Array<JFile>? {
        val directory = File(file!!.absolutePath)

        // File not found error


        if (!directory.exists()) {
            return emptyArray()
        }
        if (!directory.canRead()) {
            return emptyArray()
        }
        val listFiles: Array<File> = (if (filterOutDirs != null) if (filterOutDirs) directory.listFiles { obj: File -> obj.isFile } else directory.listFiles { obj: File -> obj.isDirectory } else directory.listFiles())
                ?: //                postError(ListingEngine.ErrorEnum.ERROR_UNKNOWN);

                return emptyArray()


        // Check Error in reading the directory (java.io.File do not allow any details about the error...).


        Arrays.sort(listFiles) { o1: File?, o2: File? -> o1!!.name.compareTo(o2!!.name) }
        return arr.cast(listFiles, N.converter(JFile::class.java) { file: File -> JFile(file) })
    }

    private fun list(filterOutDirs: Boolean?): Array<JFile>? {
        return if (VERSION.SDK_INT >= VERSION_CODES.Q) listImplQ(filterOutDirs) else listImplPie(filterOutDirs)
    }

    /**
     * @param filterOutDirs if null: unfiltered, if true: no dirs, if false: no files
     * @return
     */
    @RequiresApi(VERSION_CODES.Q)
    private fun listImplQ(filterOutDirs: Boolean?): Array<JFile>? {
        /**
         * Here we employ database queries to find the content. This reduced the time listing directory contents from 7500ms to 250ms (filtering etc included)
         * compared to using createDocFile().listFiles().
         */
        val directory = File(file!!.absolutePath)
        if (!directory.exists()) {
            return emptyArray()
        }
        try {
            val thisDoc = createDocFile()!!
            val uri: Uri = DocumentsContract.buildChildDocumentsUriUsingTree(thisDoc.uri, DocumentsContract.getDocumentId(thisDoc.uri))
            val contentResolver: ContentResolver = (AFile.getConfiguration() as AndroidFileConfiguration).context.contentResolver
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
                while (!it.isAfterLast) {
                    val name = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                    val mime = it.getString(it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE))
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
            return fileList.map { JFile(this, it) }.toTypedArray()
        } catch (e: SAFException) {
            e.printStackTrace()
        }
        return emptyArray()
    }

    override fun listContent(): Array<JFile>? {
        return list(null)
    }
}