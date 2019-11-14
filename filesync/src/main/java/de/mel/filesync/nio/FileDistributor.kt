package de.mel.filesync.nio

import de.mel.Lok
import de.mel.MelRunnable
import de.mel.auth.MelNotification
import de.mel.auth.file.AbstractFile
import de.mel.auth.service.MelAuthService
import de.mel.auth.tools.N
import de.mel.auth.tools.lock.P
import de.mel.filesync.bash.BashTools
import de.mel.filesync.data.FileSyncStrings
import de.mel.filesync.service.MelFileSyncService
import de.mel.filesync.sql.FsFile
import java.io.File
import java.io.IOException
import java.util.*

@Suppress("FINITE_BOUNDS_VIOLATION_IN_JAVA")
open class FileDistributor<T : AbstractFile>(val fileSyncService: MelFileSyncService<*>) : MelRunnable {
    private var stopped: Boolean = false;
    private var notification: MelNotification? = null
    private var running: Boolean = false
    val fileDistTaskDao = fileSyncService.fileSyncDatabaseManager.fileDistTaskDao
    val melAuthService: MelAuthService = fileSyncService.melAuthService
    val fsDao = fileSyncService.fileSyncDatabaseManager.fsDao
    val transferDao = fileSyncService.fileSyncDatabaseManager.transferDao
    val watchDogTimer = fileSyncService.indexer.indexerRunnable.fileWatcher.watchDogTimer

    companion object {

        val BUFFER_SIZE = 1024 * 64

        var factory: FileDistributorFactory? = FileDistributorFactory()
            set


        fun createInstance(fileSyncService: MelFileSyncService<*>): FileDistributor<*> = factory!!.createInstance(fileSyncService)
    }

    /**
     * this overwrites files and does not update databases!
     */
    @Throws(IOException::class)
    fun rawCopyFile(srcFile: AbstractFile, targetFile: AbstractFile) {
        var read: Int
        val out = targetFile.writer()
        val ins = srcFile.inputStream()
        try {
            do {
                if (stopped || Thread.currentThread().isInterrupted) {
                    throw InterruptedException()
                }
                val bytes = ByteArray(BUFFER_SIZE)
                read = ins.read(bytes)
                if (read > 0) {
                    out.append(bytes, 0, read)
                }
            } while (read > 0)
        } catch (e: Exception) {
            N.r {
                out.close()
                ins.close()
            }
            targetFile.delete()
        } finally {
            out.close()
            ins.close()
        }

    }


    fun isRunning() = running

    override fun getRunnableName(): String {
        return "FileDistributor for ${fileSyncService.fileSyncSettings.rootDirectory.originalFile.absolutePath}"
    }

    fun createJob(distributionTask: FileDistributionTask) {
        // unpack and store in database
        fileDistTaskDao.insert(distributionTask)
        if (!running && distributionTask.canStart()) {
            stopped = false
            fileSyncService.execute(this)
        }
    }

    fun completeJob(distributionTask: FileDistributionTask) {
        fileDistTaskDao.completeJob(distributionTask)
        if (!running && distributionTask.canStart()) {
            stopped = false
            fileSyncService.execute(this)
        }
    }


    override fun run() {
        // first setup all the nice things we need

        melAuthService.powerManager.wakeLock(this)
        running = true
        var hasTransferred = false
        try {

            if (!fileDistTaskDao.hasWork())
                return

            while (fileDistTaskDao.hasWork() && !Thread.currentThread().isInterrupted && !stopped) {
                hasTransferred = true
                val max = fileDistTaskDao.countAll()
                var countDone = fileDistTaskDao.countDone()

                N.forEachAdv(fileDistTaskDao.loadChunk()) { stoppable, index, wrapperId, fileDistributionTask ->
                    workOnDistTask(fileDistributionTask)
                    fileDistTaskDao.markDone(wrapperId)
                    showNotification(++countDone, max)
                }
            }
        } finally {
            running = false
            melAuthService.powerManager.releaseWakeLock(this)
            watchDogTimer.activate()
            watchDogTimer.start()
            if (notification != null && fileDistTaskDao.isComplete()) {
                Lok.error("DEBUG: CANCEL NOTIFICATION")
                fileDistTaskDao.deleteMarkedDone()
                notification?.cancel()
                notification = null
            }
            if (hasTransferred)
                fileSyncService.onSyncDone()
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
            notification = MelNotification(fileSyncService.uuid, FileSyncStrings.Notifications.INTENTION_PROGRESS, title, text)
            notification?.setProgress(max, current, false)
            fileSyncService.melAuthService.onNotificationFromService(fileSyncService, notification)
        } else {
            notification?.text = text
            notification?.title = title
            notification?.setProgress(max, current, false)
        }
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

        val sourceFile: T = AbstractFile.instance(distributionTask.sourceFile.absolutePath) as T

        // ...the last file is arbitrary
        val lastFile = targetStack.pop()
        val lastId = if (targetIds.empty()) null else targetIds.pop()
        val lastPath = targetPathStack.pop()

        while (!targetStack.empty()) {
            val fsId = targetIds.pop()
            val target = targetStack.pop()
            if (!target.exists()) {
                copyFile(sourceFile, target, targetPathStack.pop(), fsId)
            }
        }
        if (!lastFile.exists()) {
            if (distributionTask.deleteSource) {

                // move file
                moveFile(sourceFile, lastFile, lastPath, lastId)
                // update synced flag
                if (lastId != null)
                    P.confine(fsDao).run {
                        val fsFile = updateFs(lastId, lastFile)
                        setCreationDate(lastFile, fsFile.created.v())
                    }.end()

            } else {
                copyFile(sourceFile, lastFile, lastPath, lastId)
            }
        }
    }

    protected fun updateFs(fsId: Long?, target: T): FsFile {
        val fsBashDetails = BashTools.Companion.getFsBashDetails(target)
        val fsTarget = fsDao.getFile(fsId)
        BashTools.Companion.setCreationDate(target, fsBashDetails.created)
        fsTarget.getiNode().v(fsBashDetails.getiNode())
        fsTarget.modified.v(fsBashDetails.modified)
        fsTarget.size.v(target.length())
        fsTarget.synced.v(true)
        fsDao.update(fsTarget)
        return fsTarget
    }

    protected fun setCreationDate(file: T, timestamp: Long) {
        BashTools.Companion.setCreationDate(file, timestamp)
    }

    open protected fun moveFile(sourceFile: T, lastFile: T, lastPath: String, lastId: Long?) {
        val f = File(sourceFile.absolutePath)
        f.renameTo(File(lastFile.absolutePath))

    }

    @Throws(IOException::class)
    protected fun copyFile(source: T, target: T, targetPath: String, fsId: Long?) {
        if (target.exists())
            throw  IOException("file already exists")
        rawCopyFile(source, target)
        if (fsId != null) {
            P.confine(fsId).run {
                val fsFile = updateFs(fsId, target)
                setCreationDate(target, fsFile.created.v())
            }.end()
        }
    }

    fun moveBlocking(source: T, target: T, fsId: Long?) {
        moveFile(source, target, target.absolutePath, fsId)
    }

    fun stop() {
        stopped = false
    }


}