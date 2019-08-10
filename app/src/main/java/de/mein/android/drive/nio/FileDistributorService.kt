package de.mein.android.drive.nio

import android.app.IntentService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import de.mein.Lok
import de.mein.android.file.JFile
import de.mein.android.service.AndroidService
import de.mein.auth.MeinNotification
import de.mein.auth.tools.N
import de.mein.auth.tools.lock.T
import de.mein.drive.data.DriveStrings
import de.mein.drive.data.OldeFileDistTaskWrapper
import de.mein.drive.nio.FileDistributionTask
import de.mein.drive.service.MeinDriveService
import de.mein.drive.sql.dao.FileDistTaskDao
import de.mein.drive.sql.dao.FsDao
import de.mein.drive.sql.dao.TransferDao
import java.io.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class FileDistributorService : IntentService("FileDistributorService") {
    private lateinit var uuid: String


    //    override fun onCreate() {
//        super.onCreate()
//        Lok.debug("CREATED")
//        Lok.debug("CREATED")
//        Lok.debug("CREATED")
//        Lok.debug("CREATED")
//        Lok.debug("CREATED")
//        existingServicesSet.add(this)
//        if(existingServicesSet.size>1){
//            Lok.debug("SIZE IS: ${existingServicesSet.size}")
//            Lok.debug("SIZE IS: ${existingServicesSet.size}")
//            Lok.debug("SIZE IS: ${existingServicesSet.size}")
//            Lok.debug("SIZE IS: ${existingServicesSet.size}")
//            Lok.debug("SIZE IS: ${existingServicesSet.size}")
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        existingServicesSet.remove(this)
//
//    }
    private var notification: MeinNotification? = null

    override fun onHandleIntent(intent: Intent?) {
        // first setup all the nice things we need

        androidService = AndroidService.getInstance()
        androidService?.androidPowerManager?.wakeLock(this)
        try {
            uuid = intent!!.getStringExtra(SERVICEUUID)
            driveService = androidService!!.meinAuthService.getMeinService(uuid) as MeinDriveService<*>
            fsDao = driveService.driveDatabaseManager.fsDao
            transferDao = driveService.driveDatabaseManager.transferDao
            fileDistTaskDao = driveService.driveDatabaseManager.fileDistTaskDao

            if (!fileDistTaskDao.hasContent())
                return

            val countDown = AtomicInteger()


            while (fileDistTaskDao.hasContent()) {

                val max = fileDistTaskDao.countAll()
                val done = fileDistTaskDao.countDone()
                val countDone = AtomicInteger(done)
                showNotification(done, max)

                N.forEachAdv(fileDistTaskDao.loadChunk()) { stoppable, index, wrapperId, fileDistributionTask ->
                    workOnDistTask(fileDistributionTask)
                    fileDistTaskDao.markDone(wrapperId)
                    showNotification(countDone.incrementAndGet(), max)
                }
            }
        } finally {
            fileDistTaskDao.deleteMarkedDone()
            androidService?.androidPowerManager?.releaseWakeLock(this)
//            if (notification != null) {
//                Lok.error("DEBUG: CANCEL NOTIFICATION")
//                notification?.cancel()
//                notification = null
//            }
        }
    }

    private fun showNotification(current: Int, max: Int) {
        val title = "Moving files"
        val text = "$current/$max files moved or copied"
        if (notification == null) {
            notification = MeinNotification(uuid, DriveStrings.Notifications.INTENTION_FILES_SERVICE, title, text)
            driveService.meinAuthService.onNotificationFromService(driveService, notification)
        }
        notification!!.setProgress(max, current, false)
    }

    private fun workOnDistTask(distributionTask: FileDistributionTask) {
        distributionTask.initFromPaths()

        // do the actual work
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
//            T.lockingTransaction(transferDao!!)
//                    .run { transferDao!!.deleteByHash(distributionTask.sourceHash) }
//                    .end()

        } else {
            FileDistributorAndroidImpl.copyFile(fsDao, sourceFile, lastFile, lastPath, lastId)
        }
    }

    companion object {

//        val existingServicesSet = mutableSetOf<FileDistributorService>()

        val SERVICEUUID: String = "uuid"
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

    private lateinit var fileDistTaskDao: FileDistTaskDao
    private lateinit var driveService: MeinDriveService<*>
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

}