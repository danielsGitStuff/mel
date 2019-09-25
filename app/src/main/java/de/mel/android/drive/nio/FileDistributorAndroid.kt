package de.mel.android.drive.nio

import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import de.mel.Lok
import de.mel.android.Tools
import de.mel.android.file.JFile
import de.mel.android.file.SAFAccessor
import de.mel.android.service.AndroidService
import de.mel.auth.tools.N
import de.mel.auth.tools.NWrap
import de.mel.auth.tools.lock.Transaction
import de.mel.drive.nio.FileDistributor
import de.mel.drive.service.MelDriveService
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.Exception

class FileDistributorAndroid(driveService: MelDriveService<*>) : FileDistributor<JFile>(driveService) {

    override fun moveFile(sourceFile: JFile, target: JFile, targetPath: String, fsId: Long?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val srcParentDoc = sourceFile.parentFile.createDocFile()
            val srcDoc = sourceFile.createDocFile()
            val targetParentDoc = target.parentFile.createDocFile()
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
            if (srcDoc == null)
                Lok.debug()
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