package de.mein.android.drive.nio

import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import de.mein.Lok
import de.mein.android.Tools
import de.mein.android.file.JFile
import de.mein.android.file.SAFAccessor
import de.mein.android.service.AndroidService
import de.mein.auth.tools.N
import de.mein.auth.tools.NWrap
import de.mein.auth.tools.lock.Transaction
import de.mein.drive.nio.FileDistributor
import de.mein.drive.service.MeinDriveService
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.Exception

class FileDistributorAndroid(driveService: MeinDriveService<*>) : FileDistributor<JFile>(driveService) {

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