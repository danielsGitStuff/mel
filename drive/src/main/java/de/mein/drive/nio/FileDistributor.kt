package de.mein.drive.nio

import de.mein.Lok
import de.mein.MeinRunnable
import de.mein.auth.MeinNotification
import de.mein.auth.file.AFile
import de.mein.auth.service.MeinAuthService
import de.mein.auth.tools.N
import de.mein.drive.bash.BashTools
import de.mein.drive.data.DriveStrings
import de.mein.drive.service.MeinDriveService
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

@Suppress("FINITE_BOUNDS_VIOLATION_IN_JAVA")
open class FileDistributor<T : AFile<*>>(val driveService: MeinDriveService<*>) : MeinRunnable {
    private var notification: MeinNotification? = null
    private var running: Boolean = false
    val fileDistTaskDao = driveService.driveDatabaseManager.fileDistTaskDao
    val meinAuthService: MeinAuthService = driveService.meinAuthService
    val fsDao = driveService.driveDatabaseManager.fsDao
    val transferDao = driveService.driveDatabaseManager.transferDao
    val watchDogTimer = driveService.indexer.indexerRunnable.indexWatchdogListener.watchDogTimer

    companion object {
        //        var factory: FileDistributorFactory = FileDistributorFactory()
//            set
        val BUFFER_SIZE = 1024 * 64

        var factory: FileDistributorFactory? = null
            set

        @Throws(IOException::class)
        fun copyStream(inputStream: InputStream, out: OutputStream) {
            var read = 0
            do {
                val bytes = ByteArray(BUFFER_SIZE)
                read = inputStream.read(bytes)
                if (read > 0) {
                    out.write(bytes, 0, read)
                }
            } while (read > 0)

        }

        fun createInstance(driveService: MeinDriveService<*>): FileDistributor<*> = factory!!.createInstance(driveService)
    }

    fun isRunning() = running

    override fun getRunnableName(): String {
        return "FileDistributor for ${driveService.driveSettings.rootDirectory.originalFile.absolutePath}"
    }

    fun createJob(distributionTask: FileDistributionTask) {
        // unpack and store in database
        fileDistTaskDao.insert(distributionTask)
        if (!running)
            driveService.execute(this)
    }

    override fun run() {
        // first setup all the nice things we need

        meinAuthService.powerManager.wakeLock(this)
        try {

            if (!fileDistTaskDao.hasContent())
                return

            while (fileDistTaskDao.hasContent() && !Thread.currentThread().isInterrupted && running) {

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
            running = false
            meinAuthService.powerManager.releaseWakeLock(this)
            watchDogTimer.activate()
            watchDogTimer.start()
            if (notification != null) {
                Lok.error("DEBUG: CANCEL NOTIFICATION")
                notification?.cancel()
                notification = null
            }
        }
    }

    override fun onStart() {
        running = true
        watchDogTimer.deactivate()
    }

    private fun showNotification(current: Int, max: Int) {
        val title = "Moving files"
        val text = "$current/$max files moved or copied"
        if (notification == null) {
            notification = MeinNotification(driveService.uuid, DriveStrings.Notifications.INTENTION_FILES_SERVICE, title, text)
            driveService.meinAuthService.onNotificationFromService(driveService, notification)
        }
        notification?.setProgress(max, current, false)
    }

    private fun workOnDistTask(distributionTask: FileDistributionTask) {
        distributionTask.initFromPaths()

        // do the actual work
        val targetStack = Stack<T>()
        distributionTask.targetFiles.forEach { targetStack.push(it as T) }

        val targetPathStack = Stack<String>()
        targetPathStack.addAll(distributionTask.targetPaths)

        val targetIds = Stack<Long>()
        targetIds.addAll(distributionTask.targetFsIds)

        val sourceFile: T = AFile.instance(distributionTask.sourceFile.absolutePath) as T
        // ...the last file is arbitrary

        val lastFile = targetStack.pop()
        val lastId = if (targetIds.empty()) null else targetIds.pop()
        val lastPath = targetPathStack.pop()

        while (!targetStack.empty()) {
            val fsId = targetIds.pop()
            val target = targetStack.pop()
            if (!target.exists()) {
                copyFile(sourceFile, target, targetPathStack.pop(), fsId)
                de.mein.auth.tools.lock.T.lockingTransaction(fsId).run { updateFs(fsId, target) }.end()
            }
        }
        if (!lastFile.exists()) {
            if (distributionTask.deleteSource) {

                // move file
                moveFile(sourceFile, lastFile, lastPath, lastId)
                // update synced flag
                if (lastId != null)
                    de.mein.auth.tools.lock.T.lockingTransaction(fsDao).run { updateFs(lastId, lastFile) }.end()

            } else {
                copyFile(sourceFile, lastFile, lastPath, lastId)
                if (lastId != null) {
                    de.mein.auth.tools.lock.T.lockingTransaction(fsDao).run { updateFs(lastId, lastFile) }.end()
                }
            }
        }
    }

    private fun updateFs(fsId: Long?, target: T) {
        val fsBashDetails = BashTools.getFsBashDetails(target)
        val fsTarget = fsDao.getFile(fsId)
        fsTarget.getiNode().v(fsBashDetails.getiNode())
        fsTarget.modified.v(fsBashDetails.modified)
        fsTarget.size.v(target.length())
        fsTarget.synced.v(true)
        fsDao.update(fsTarget)

    }

    open protected fun moveFile(sourceFile: T, lastFile: T, lastPath: String, lastId: Long?) {
        val f = File(sourceFile.absolutePath)
        f.renameTo(File(lastFile.absolutePath))

    }

    open protected fun copyFile(source: T, target: T, targetPath: String, fsId: Long?) {
        val input = source.inputStream()
        if (target.exists())
            return
        try {
            val out = target.outputStream()
            copyStream(input, out)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            input.close()
        }
    }

    fun moveBlocking(source: T, target: T, fsId: Long?) {
        moveFile(source, target, target.absolutePath, fsId)
    }

    fun stop() {
        running = false
    }


}