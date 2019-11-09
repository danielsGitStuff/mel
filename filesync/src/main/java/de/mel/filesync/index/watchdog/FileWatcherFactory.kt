package de.mel.filesync.index.watchdog

import de.mel.Lok
import de.mel.filesync.service.MelFileSyncService
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.WatchService

open class FileWatcherFactory {

    open fun runInstance(melFsSyncService: MelFileSyncService<*>): FileWatcher {
        var watchService: WatchService? = null
        val watchdogListener: FileWatcher
        try {
            watchService = FileSystems.getDefault().newWatchService()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        watchdogListener = if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            Lok.debug("WatchDog.windows")
            FileWatcherWindows(melFsSyncService, watchService)
        } else {
            Lok.debug("WatchDog.unix")
            FileWatcherUnix(melFsSyncService, watchService)
        }
        watchdogListener.melFileSyncService = melFsSyncService
        watchdogListener.melFileSyncService.execute(watchdogListener)
        return watchdogListener
    }

    companion object {
        var factory: FileWatcherFactory = FileWatcherFactory()
    }
}