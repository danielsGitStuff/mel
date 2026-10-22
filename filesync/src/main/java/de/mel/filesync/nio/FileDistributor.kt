package de.mel.filesync.nio

import de.mel.Lok
import de.mel.MelRunnable
import de.mel.auth.MelNotification
import de.mel.auth.file.AbstractFile
import de.mel.auth.file.IFile
import de.mel.auth.service.MelAuthService
import de.mel.auth.tools.N
import de.mel.filesync.bash.BashTools
import de.mel.filesync.data.FileSyncStrings
import de.mel.filesync.service.MelFileSyncService
import de.mel.filesync.sql.FsFile
import java.io.File
import java.io.IOException
import java.util.*

@Suppress("FINITE_BOUNDS_VIOLATION_IN_JAVA")
open class FileDistributor<T : IFile>(val fileSyncService: MelFileSyncService<*>) : MelRunnable {
    /**
     * Set while the service is suspended: the thread ends after the task it works on, jobs that come meanwhile wait in the database until [resume].
     */
    @Volatile
    private var stopped: Boolean = false
    private var notification: MelNotification? = null
    private var running: Boolean = false

    /**
     * Only one thread works on the tasks. The distributor is started by whoever adds a job, and two of them can come before the first thread says it runs:
     * two threads would load the same tasks and copy, move and delete the same files at the same time.
     */
    private val active = java.util.concurrent.atomic.AtomicBoolean(false)
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

    fun isRunning() = running

    override fun getRunnableName(): String {
        return "FileDistributor for ${fileSyncService.fileSyncSettings.rootDirectory.originalFile.absolutePath}"
    }

    fun createJob(distributionTask: FileDistributionTask) {
        // unpack and store in database
        fileDistTaskDao.insert(distributionTask)
        startIfIdle(distributionTask)
    }

    fun completeJob(distributionTask: FileDistributionTask) {
        fileDistTaskDao.completeJob(distributionTask)
        startIfIdle(distributionTask)
    }

    private fun startIfIdle(distributionTask: FileDistributionTask) {
        if (!stopped && !active.get() && distributionTask.canStart()) {
            fileSyncService.execute(this)
        }
    }


    override fun run() {
        // another thread is at work: it takes the tasks that are there, and checks again when it is done
        if (!active.compareAndSet(false, true))
            return
        // first setup all the nice things we need

        melAuthService.powerManager.wakeLock(this)
        running = true
        var hasTransferred = false
        var finishedNormally = false
        try {

            if (!fileDistTaskDao.hasWork()) {
                finishedNormally = true
                return
            }

            while (fileDistTaskDao.hasWork() && !Thread.currentThread().isInterrupted && !stopped) {
                hasTransferred = true
                val max = fileDistTaskDao.countAll()
                var countDone = fileDistTaskDao.countDone()

                N.forEachAdv(fileDistTaskDao.loadChunk()) { stoppable, index, wrapperId, fileDistributionTask ->
                    if (stopped) {
                        stoppable.stop()
                        return@forEachAdv
                    }
                    workOnDistTask(fileDistributionTask)
                    fileDistTaskDao.markDone(wrapperId)
                    showNotification(++countDone, max)
                }
            }
            finishedNormally = true
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
            active.set(false)
            // a job that came while this was finishing found a busy distributor and started nothing. (After a failure not: that would try the same task again and again)
            if (finishedNormally && !stopped && fileDistTaskDao.hasWork())
                fileSyncService.execute(this)
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
                copyFile(sourceFile, target, targetPathStack.pop(), fsId, fileSyncService.fsConvenience.copiesAreOptional, distributionTask.sourceHash)
            }
        }
        if (!lastFile.exists()) {
            if (distributionTask.deleteSource) {

                // the file keeps its time when it is moved: give it the one of the fs entry first
                fileSyncService.fsConvenience.applyModified(sourceFile, lastId?.let { fsDao.getFile(it) })
                // move file
                moveFile(sourceFile, lastFile, lastPath, lastId)
                // update synced flag
                if (lastId != null)
                    de.mel.auth.tools.lock2.P.confine(fsDao).run {
                        updateFs(lastId, lastFile, distributionTask.sourceHash)
                    }.end()

            } else {
                copyFile(sourceFile, lastFile, lastPath, lastId, hash = distributionTask.sourceHash)
            }
        }
    }

    /**
     * Call with the lock of the fs table: a stage set that is committed meanwhile puts its copy of the table in place at its end and would undo this.
     * @return null if the entry is gone or wants other content now (changed while the file was moved or copied): it is not this file's any more
     */
    protected fun updateFs(fsId: Long?, target: T, hash: String?): FsFile? {
        val fsTarget = fsDao.getFile(fsId)
        if (fsTarget == null || (hash != null && hash != fsTarget.contentHash.v())) {
            Lok.debug("fs entry $fsId gone or changed while ${target.absolutePath} was placed")
            return null
        }
        // the time of the sender was applied to the file (FsConvenience), if the file system took it. What the disk says is what the fs table gets.
        val fsBashDetails = BashTools.getFsBashDetails(target)
                ?: throw IOException("could not read FsBashDetails from ${target.absolutePath}")
        // what the disk says: the creation time is not sent, every device has its own
        fsTarget.created.v(fsBashDetails.created)
        fsTarget.getiNode().v(fsBashDetails.getiNode())
        fsTarget.modified.v(fsBashDetails.modified)
        fsTarget.size.v(target.length())
        fsTarget.synced.v(true)
        fsDao.update(fsTarget)
        return fsTarget
    }

    open protected fun moveFile(sourceFile: T, lastFile: T, lastPath: String, lastId: Long?) {
        val f = File(sourceFile.absolutePath)
        f.renameTo(File(lastFile.absolutePath))

    }

    @Throws(IOException::class)
    protected fun copyFile(source: T, target: T, targetPath: String, fsId: Long?, mayBeLeftOut: Boolean = false, hash: String? = null) {
        if (target.exists())
            throw  IOException("file already exists")
        val convenience = fileSyncService.fsConvenience
        // the copy is made without a lock (it may take a while), what the fs table gets is written with the lock of the table (see updateFs)
        if (convenience.copy(source, target) == CopyResult.NOT_DONE) {
            if (!mayBeLeftOut)
                throw IOException("could not copy ${source.absolutePath} to ${target.absolutePath}")
            // this place was only wanted if it costs nothing
            Lok.debug("no copy for ${target.absolutePath}: left out")
            if (fsId != null)
                de.mel.auth.tools.lock2.P.confine(fsDao).run { fsDao.deleteById(fsId) }.end()
            return
        }
        if (fsId != null) {
            de.mel.auth.tools.lock2.P.confine(fsDao).run {
                val fsFile = fsDao.getFile(fsId)
                if (fsFile != null)
                    convenience.applyModified(target, fsFile)
                updateFs(fsId, target, hash)
            }.end()
        }
    }

    /**
     * @throws IOException if the file is not at the target afterwards, or still at the source
     */
    @Throws(IOException::class)
    fun moveBlocking(source: T, target: T, fsId: Long?) {
        moveFile(source, target, target.absolutePath, fsId)
        if (!target.exists() || source.exists())
            throw IOException("could not move ${source.absolutePath} to ${target.absolutePath}")
    }

    fun stop() {
        stopped = true
    }

    fun resume() {
        stopped = false
        if (!active.get() && fileDistTaskDao.hasWork())
            fileSyncService.execute(this)
    }


}