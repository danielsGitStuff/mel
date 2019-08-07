package de.mein.android.drive.nio

import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import de.mein.Lok
import de.mein.android.Tools
import de.mein.android.file.JFile
import de.mein.android.file.SAFAccessor
import de.mein.android.service.AndroidService
import de.mein.android.service.CopyService
import de.mein.auth.file.AFile
import de.mein.auth.tools.N
import de.mein.auth.tools.NWrap
import de.mein.auth.tools.lock.T
import de.mein.auth.tools.lock.Transaction
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer
import de.mein.drive.bash.BashTools
import de.mein.drive.nio.FileDistributorImpl
import de.mein.drive.nio.FileJob
import de.mein.drive.service.sync.SyncHandler
import de.mein.drive.sql.dao.FsDao
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class FileDistributorAndroidImpl : FileDistributorImpl {
    private lateinit var syncHandler: SyncHandler

    override fun init(syncHandler: SyncHandler) {
        this.syncHandler = syncHandler;
    }

    override fun workOnTask(fileJob: FileJob) {
        val serviceIntent = Intent(Tools.getApplicationContext(), FileDistributorService::class.java)
        val json = SerializableEntitySerializer.serialize(fileJob.distributionTask)
        serviceIntent.putExtra(FileDistributorService.TASK, json)
        Tools.getApplicationContext().startService(serviceIntent)
    }


    override fun moveBlocking(source: AFile<*>, target: AFile<*>, fsId: Long?, fsDao: FsDao?) {
        moveFile(AndroidService.getInstance(), fsDao, source as JFile, target as JFile, target.absolutePath, fsId)
    }

    companion object {
        fun moveFile(androidService: AndroidService, fsDao: FsDao?, sourceFile: JFile, target: JFile, targetPath: String, fsId: Long?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

                val srcParentDoc = sourceFile.parentFile.createDocFile()
                val srcDoc = sourceFile.createDocFile()
                val targetParentDoc = target.parentFile.createDocFile()
                if (target.exists())
                    return
                val movedUri = DocumentsContract.moveDocument(androidService!!.contentResolver, srcDoc.uri, srcParentDoc.uri, targetParentDoc.uri)
                if (!sourceFile.name.equals(target.name)) {
                    DocumentsContract.renameDocument(androidService!!.contentResolver, movedUri, target.name)
                }
                if (fsId != null) {
                    val transaction = T.lockingTransaction(fsDao)
                    try {
                        val fsBashDetails = BashTools.getFsBashDetails(target)
                        val fsTarget = fsDao!!.getFile(fsId)
                        fsTarget.getiNode().v(fsBashDetails.getiNode())
                        fsTarget.modified.v(fsBashDetails.modified)
                        fsTarget.size.v(target.length())
                        fsTarget.synced.v(true)
                        fsDao.update(fsTarget)
                    } catch (e: java.lang.Exception) {
                        e.printStackTrace()
                    } finally {
                        transaction.end()
                    }
                }
            } else {
                copyFile(fsDao, sourceFile, target, targetPath, fsId)
                sourceFile.delete()
            }
        }

        fun copyFile(fsDao: FsDao?, sourceFile: JFile, target: JFile, targetPath: String, fsId: Long?) {
            val fis = NWrap<InputStream>(null)
            val fos = NWrap<OutputStream>(null)
            var transaction: Transaction<*>? = null
            try {
                val srcDoc = sourceFile.createDocFile()
                var targetDoc: DocumentFile? = target.createDocFile()
                if (targetDoc == null) {
                    val targetParentDoc = target.createParentDocFile()
                            ?: throw FileNotFoundException("directory does not exist: $targetPath")
                    val jtarget = JFile(target)
                    jtarget.createNewFile()
                    targetDoc = target.createDocFile()
                }
                val resolver = Tools.getApplicationContext().contentResolver
                fis.v = resolver.openInputStream(srcDoc.getUri())
                fos.v = resolver.openOutputStream(targetDoc!!.uri)
                FileDistributorService.copyStream(fis.v, fos.v)
                // update DB
                if (fsId != null) {
                    transaction = T.lockingTransaction(fsDao)
                    val fsBashDetails = BashTools.getFsBashDetails(target)
                    val fsTarget = fsDao!!.getFile(fsId)
                    fsTarget.getiNode().v(fsBashDetails.getiNode())
                    fsTarget.modified.v(fsBashDetails.modified)
                    fsTarget.size.v(target.length())
                    fsTarget.synced.v(true)
                    fsDao!!.update(fsTarget)
                }
            } catch (e: SAFAccessor.SAFException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                N.s { fis.v.close() }
                N.s { fos.v.close() }
                transaction?.end()
            }
        }
    }
}