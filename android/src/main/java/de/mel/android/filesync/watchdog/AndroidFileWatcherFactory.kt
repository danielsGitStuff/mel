package de.mel.android.filesync.watchdog


import de.mel.filesync.index.watchdog.FileWatcher
import de.mel.filesync.index.watchdog.FileWatcherFactory
import de.mel.filesync.service.MelFileSyncService

class AndroidFileWatcherFactory : FileWatcherFactory() {
    override fun runInstance(melFsSyncService: MelFileSyncService<*>): FileWatcher {
        return RecursiveWatcher(melFsSyncService)
    }
}