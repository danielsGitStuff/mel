package de.mel.android.filesync.nio

import android.os.Build
import android.provider.DocumentsContract
import de.mel.Lok
import de.mel.android.file.AndroidFile
import de.mel.android.service.AndroidService
import de.mel.filesync.nio.FileDistributor
import de.mel.filesync.service.MelFileSyncService

class FileDistributorAndroid(fileSyncService: MelFileSyncService<*>) : FileDistributor<AndroidFile>(fileSyncService) {

    override fun moveFile(sourceFile: AndroidFile, target: AndroidFile, targetPath: String, fsId: Long?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val srcParentDoc = sourceFile.parentFile.createDocFile()!!
            val srcDoc = sourceFile.createDocFile()!!
            val targetParentDoc = target.parentFile.createDocFile()!!
            if (target.exists())
                return
            if (target.name.contains("?")) {
                // if the target files name contains a "?" "DocumentsContract.renameDocument()" will change that to "_".
                // as a workaround, the file is copied and then deleted. SAF likely stands for "Stpecial Acces Framewörk"
                // rather than "Storage Access Framework". Because it is really bad at the latter.
                // https://issuetracker.google.com/issues/139511261
                Lok.debug("Here is a reminder that Android's SAF sucks!")
                copyFile(sourceFile, target, targetPath, fsId)
                sourceFile.delete()
                return
            }
            var movedUri = DocumentsContract.moveDocument(AndroidService.getInstance()!!.contentResolver, srcDoc.uri, srcParentDoc.uri, targetParentDoc.uri)
            val oldeUri = movedUri
            if (!sourceFile.name.equals(target.name)) {
                movedUri = DocumentsContract.renameDocument(AndroidService.getInstance()!!.contentResolver, movedUri, target.name)
            }
            if (!target.exists())
                Lok.debug()
        } else {
            copyFile(sourceFile, target, targetPath, fsId)
            sourceFile.delete()
        }
    }
}