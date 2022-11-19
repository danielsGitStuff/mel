package de.mel.android.filesync.watchdog

import android.os.Build
import de.mel.filesync.index.watchdog.FileWatcher
import de.mel.filesync.index.watchdog.FileWatcherFactory
import de.mel.filesync.service.MelFileSyncService

class AndroidFileWatcherFactory : FileWatcherFactory() {
    override fun runInstance(melFsSyncService: MelFileSyncService<*>): FileWatcher {
        return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            SAFFileWatcher(melFsSyncService)
        } else
            RecursiveWatcher(melFsSyncService)
    }
}