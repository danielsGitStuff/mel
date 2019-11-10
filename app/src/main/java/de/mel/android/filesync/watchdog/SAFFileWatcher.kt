package de.mel.android.filesync.watchdog

import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import de.mel.Lok
import de.mel.android.file.AndroidFile
import de.mel.android.file.AndroidFileConfiguration
import de.mel.android.service.AndroidService
import de.mel.auth.file.AbstractFile
import de.mel.filesync.index.watchdog.FileWatcher
import de.mel.filesync.service.MelFileSyncService
import java.lang.Exception

@RequiresApi(api = Build.VERSION_CODES.Q)
class SAFFileWatcher(melFileSyncService: MelFileSyncService<*>) : FileWatcher(melFileSyncService) {

    private val contentObserver = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            Lok.debug("something happened to $uri")
            if (uri != null)
                AndroidService.getInstance()?.contentResolver?.let { contentResolver ->
                    val id = uri.toString().split("/").last()
                    val selection = "${MediaStore.Files.FileColumns.DOCUMENT_ID}"
                    val cursor = contentResolver.query(uri, null, null, null)
                    cursor?.use {
                        try {
                            it.moveToFirst()
                            val colIndex = it.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                            val path = it.getString(colIndex)
                            Lok.debug("got from resolver: $path")
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                }
        }
    }

    override fun getRunnableName(): String = "file watcher for ${melFileSyncService.runnableName}"

    override fun watchDirectory(dir: AbstractFile<out AbstractFile<*>>?) {
        Lok.debug("trying to register observer for ${dir?.absolutePath}")
        AndroidService.getInstance()?.contentResolver?.let {
            val androidFile = dir as AndroidFile
            Lok.debug("registering observer for ${androidFile.absolutePath}")
            val uri = androidFile.getDocFile()!!.uri
            val config = AbstractFile.getConfiguration() as AndroidFileConfiguration
            val root = melFileSyncService.fileSyncSettings.rootDirectory.originalFile as AndroidFile

            it.registerContentObserver(root.getDocFile()!!.uri, true, contentObserver)
//            MediaStore.Files.getContentUri()


//works
//            it.registerContentObserver(MediaStore.Files.getContentUri("external"), true, contentObserver)


        }

    }

    override fun runImpl() {
    }
}