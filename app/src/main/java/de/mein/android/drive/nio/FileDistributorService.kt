package de.mein.android.drive.nio

import android.app.IntentService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import de.mein.Lok
import de.mein.android.Tools
import de.mein.android.file.JFile
import de.mein.android.file.SAFAccessor
import de.mein.android.service.AndroidService
import de.mein.auth.file.AFile
import de.mein.auth.tools.N
import de.mein.auth.tools.NWrap
import de.mein.auth.tools.lock.T
import de.mein.auth.tools.lock.Transaction
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer
import de.mein.drive.bash.BashTools
import de.mein.drive.nio.FileDistributionTask
import de.mein.drive.service.MeinDriveService
import de.mein.drive.sql.dao.FsDao
import de.mein.drive.sql.dao.TransferDao
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

class FileDistributorService : IntentService("FileDistributorService") {
    override fun onHandleIntent(intent: Intent?) {
        // first setup all the nice things we need

        androidService = AndroidService.getInstance()
        androidService?.androidPowerManager?.wakeLock(this)
        try {


            val json = intent!!.getStringExtra(TASK)
            distributionTask = (SerializableEntityDeserializer.deserialize(json) as FileDistributionTask?)!!
            distributionTask.initFromPaths()

            val driveService = androidService!!.meinAuthService.getMeinService(distributionTask.serviceUuid) as MeinDriveService<*>
            fsDao = driveService.driveDatabaseManager.fsDao
            transferDao = driveService.driveDatabaseManager.transferDao

//        // do the actual work
            val targetStack = Stack<JFile>()
            distributionTask.targetFiles.forEach { targetStack.push(it as JFile) }

            val targetPathStack = Stack<String>()
            targetPathStack.addAll(distributionTask.targetPaths)

            val targetIds = Stack<Long>()
            targetIds.addAll(distributionTask.targetFsIds)

            val sourceFile = JFile(distributionTask.sourceFile.absolutePath)
            // ...the last file is arbitrary

            val lastFile = targetStack.pop()
            val lastId = if (targetIds.empty()) null else targetIds.pop()
            val lastPath = targetPathStack.pop()

            while (!targetStack.empty()) {
                FileDistributorAndroidImpl.copyFile(fsDao, sourceFile, targetStack.pop(), targetPathStack.pop(), targetIds.pop())
            }
            if (distributionTask.deleteSource) {
                // move file
                FileDistributorAndroidImpl.moveFile(androidService!!, fsDao, sourceFile, lastFile, lastPath, lastId)
                // update synced flag
                T.lockingTransaction(fsDao).run { fsDao.setSynced(lastId, true) }.end()

                // delete from transfer
                T.lockingTransaction(transferDao!!)
                        .run { transferDao!!.deleteByHash(distributionTask.sourceHash) }
                        .end()

            } else {
                FileDistributorAndroidImpl.copyFile(fsDao, sourceFile, lastFile, lastPath, lastId)
            }
        } finally {
            androidService?.androidPowerManager?.releaseWakeLock(this)
        }
    }

    companion object {
        val TASK = "task"
        val BUFFER_SIZE = 1024 * 64

        @Throws(IOException::class)
        fun copyStream(`in`: InputStream, out: OutputStream) {
            var read = 0
            do {
                val bytes = ByteArray(BUFFER_SIZE)
                read = `in`.read(bytes)
                if (read > 0) {
                    out.write(bytes, 0, read)
                }
            } while (read > 0)
        }
    }

    private var transferDao: TransferDao? = null
    private var androidService: AndroidService? = null
    private var serviceConnection: ServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName,
                                        binder: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val localBinder = binder as AndroidService.LocalBinder
            androidService = localBinder.service
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            androidService = null
        }
    }

    fun bindService(conn: ServiceConnection): Boolean {
        val intent = Intent(baseContext, AndroidService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        return true
    }

    lateinit var distributionTask: FileDistributionTask
    lateinit var fsDao: FsDao


//    private fun moveFile(sourceFile: JFile, target: JFile, targetPath: String, fsId: Long?) {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//
//            val srcParentDoc = sourceFile.parentFile.createDocFile()
//            val srcDoc = sourceFile.createDocFile()
//            val targetParentDoc = target.parentFile.createDocFile()
//            if (target.exists())
//                return
//            val movedUri = DocumentsContract.moveDocument(androidService!!.contentResolver, srcDoc.uri, srcParentDoc.uri, targetParentDoc.uri)
//            if (!sourceFile.name.equals(target.name)) {
//                DocumentsContract.renameDocument(androidService!!.contentResolver, movedUri, target.name)
//            }
//            if (fsId != null) {
//                val transaction = T.lockingTransaction(fsDao)
//                try {
//                    val fsBashDetails = BashTools.getFsBashDetails(target)
//                    val fsTarget = fsDao.getFile(fsId)
//                    fsTarget.getiNode().v(fsBashDetails.getiNode())
//                    fsTarget.modified.v(fsBashDetails.modified)
//                    fsTarget.size.v(target.length())
//                    fsTarget.synced.v(true)
//                    fsDao.update(fsTarget)
//                } catch (e: java.lang.Exception) {
//                    e.printStackTrace()
//                } finally {
//                    transaction.end()
//                }
//            }
//        } else {
//            copyFile(sourceFile, target, targetPath, fsId)
//            sourceFile.delete()
//        }
//    }
//
//    private fun copyFile(sourceFile: JFile, target: JFile, targetPath: String, fsId: Long?) {
//        val fis = NWrap<InputStream>(null)
//        val fos = NWrap<OutputStream>(null)
//        var transaction: Transaction<*>? = null
//        try {
//            val srcDoc = sourceFile.createDocFile()
//            var targetDoc: DocumentFile? = target.createDocFile()
//            if (targetDoc == null) {
//                val targetParentDoc = target.createParentDocFile()
//                        ?: throw FileNotFoundException("directory does not exist: $targetPath")
//                val jtarget = JFile(target)
//                jtarget.createNewFile()
//                targetDoc = target.createDocFile()
//            }
//            val resolver = Tools.getApplicationContext().contentResolver
//            fis.v = resolver.openInputStream(srcDoc.getUri())
//            fos.v = resolver.openOutputStream(targetDoc!!.uri)
//            copyStream(fis.v, fos.v)
//            // update DB
//            if (fsId != null) {
//                transaction = T.lockingTransaction(fsDao)
//                val fsBashDetails = BashTools.getFsBashDetails(target)
//                val fsTarget = fsDao.getFile(fsId)
//                fsTarget.getiNode().v(fsBashDetails.getiNode())
//                fsTarget.modified.v(fsBashDetails.modified)
//                fsTarget.size.v(target.length())
//                fsTarget.synced.v(true)
//                fsDao.update(fsTarget)
//            }
//        } catch (e: SAFAccessor.SAFException) {
//            e.printStackTrace()
//        } catch (e: IOException) {
//            e.printStackTrace()
//        } finally {
//            N.s { fis.v.close() }
//            N.s { fos.v.close() }
//            transaction?.end()
//        }
//    }


}

//    fun bla(): Unit {
//        val move = intent!!.getBooleanExtra(MOVE, false)
//        val srcPath = intent!!.getStringExtra(SRC_PATH)
//        val targetPath = intent!!.getStringExtra(TRGT_PATH)
//        val src = JFile(srcPath)
//        val target = JFile(targetPath)
//        val msg = (if (move) "moving" else "copying") + " '" + srcPath + "' -> '" + targetPath + "'"
//        Log.d(javaClass.simpleName, msg)
//        val fis = NWrap<InputStream>(null)
//        val fos = NWrap<OutputStream>(null)
//        try {
//            val srcDoc = src.createDocFile()
//            var targetDoc: DocumentFile? = target.createDocFile()
//            if (targetDoc == null) {
//                val targetParentDoc = target.createParentDocFile()
//                        ?: throw FileNotFoundException("directory does not exist: $targetPath")
//                val jtarget = JFile(target)
//                jtarget.createNewFile()
//                targetDoc = target.createDocFile()
//            }
//            val resolver = Tools.getApplicationContext().contentResolver
//            fis.v = resolver.openInputStream(srcDoc.getUri())
//            fos.v = resolver.openOutputStream(targetDoc.uri)
//
//            copyStream(fis.v, fos.v)
//            if (move) {
//                srcDoc.delete()
//            }
//        } catch (e: SAFAccessor.SAFException) {
//            e.printStackTrace()
//        } catch (e: IOException) {
//            e.printStackTrace()
//        } finally {
//            N.s { fis.v.close() }
//            N.s { fos.v.close() }
//        }
//        Lok.debug("CopyService.onHandleIntent")
//    }



