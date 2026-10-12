package de.mel.android.filesync.watchdog

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.FileObserver
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import de.mel.Lok
import de.mel.android.Tools
import de.mel.android.file.*
import de.mel.android.service.AndroidService
import de.mel.auth.file.AbstractFile
import de.mel.auth.file.IFile
import de.mel.filesync.index.watchdog.FileWatcher
import de.mel.filesync.service.MelFileSyncService
import java.io.File
@Deprecated(message = "fuck SAF")
@RequiresApi(api = Build.VERSION_CODES.Q)
class SAFFileWatcher(melFileSyncService: MelFileSyncService<*>) : FileWatcher(melFileSyncService) {

    private lateinit var obs: FileObserver
    private val contentObserver = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            Lok.debug("something happened to $uri")
            if (uri != null)
                AndroidService.getInstance()?.let { androidService ->
                    androidService.contentResolver?.let { contentResolver ->
                        val volume = MediaStore.getVolumeName(uri)
                        Lok.debug("on volume $volume")
                        val write = ContextCompat.checkSelfPermission(androidService, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                        val read = ContextCompat.checkSelfPermission(androidService, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                        Lok.debug("read: $read write: $write")
                        val provider = contentResolver.acquireContentProviderClient(uri)
                        contentResolver.query(uri, null, null, null, null).use {

                        }
                    }
                }
        }
    }

    override fun getRunnableName(): String = "file watcher for ${melFileSyncService.runnableName}"

    override fun watchDirectory(dir: IFile?) {
        Lok.debug("trying to register observer for ${dir?.absolutePath}")

        AndroidService.getInstance()?.contentResolver?.let {
            val androidFile = dir as SAFFile
            Lok.debug("registering observer for ${androidFile.absolutePath}")
            val docUri = androidFile.getDocFile()!!.uri
            val config = AbstractFile.configuration as SAFFileConfiguration
            val root = melFileSyncService.fileSyncSettings.rootDirectory.originalFile as SAFFile

            obs = object : FileObserver(File(dir.absolutePath)) {
                override fun onEvent(event: Int, path: String?) {
                    Lok.debug("DODODODODOD")
                }

            }
            obs.startWatching()

            val uuu = root.getDocFile()!!.uri
            it.registerContentObserver(docUri, true, contentObserver)
            //content://com.android.externalstorage.documents/tree/primary%3A/document/primary%3ASYNC

            val extUriString = Tools.getSharedPreferences().getString(SAFAccessor.EXT_SD_CARD_URI, null)
            val treeUri: Uri? = Uri.parse(extUriString)
            it.registerContentObserver(treeUri!!, true, contentObserver)
            it.registerContentObserver(uuu, true, contentObserver)


//FileObserver

//            it.registerContentObserver(root.getDocFile()!!.uri, true, contentObserver)
//            DocumentsContract.Document.COLUMN_DOCUMENT_ID
//            it.query(docUri,)

//            val treeUriString = Tools.getSharedPreferences().getString(SAFAccessor.EXT_SD_CARD_URI, null)
//            val treeUri: Uri? = Uri.parse(treeUriString)
//            it.registerContentObserver(treeUri!!, true, contentObserver)

//            val treeUriString = Tools.getSharedPreferences().getString(SAFAccessor.EXT_SD_CARD_URI, "NEIN!")

//            val treeUri: Uri? = Uri.parse(treeUriString)
//            val rootFile = DocumentFile.fromTreeUri(Tools.getApplicationContext(), treeUri!!)
//            it.registerContentObserver(treeUri!!, true, contentObserver)

            // works, but returns all changes to the external storage
            it.registerContentObserver(MediaStore.Files.getContentUri("external"), true, contentObserver)
        }

    }

    override fun runImpl() {
    }
}