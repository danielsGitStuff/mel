package de.mel.android.filesync.watchdog

import de.mel.auth.file.AbstractFile
import de.mel.filesync.index.watchdog.FileWatcher
import de.mel.filesync.service.MelFileSyncService

class SAFFileWatcher(melFileSyncService: MelFileSyncService<*>) : FileWatcher(melFileSyncService) {

    override fun getRunnableName(): String = "file watcher for ${melFileSyncService.runnableName}"

    override fun watchDirectory(dir: AbstractFile<out AbstractFile<*>>?) {}

    override fun runImpl() {}
}