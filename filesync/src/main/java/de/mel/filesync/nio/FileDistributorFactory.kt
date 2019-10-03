package de.mel.filesync.nio

import de.mel.filesync.service.MelFileSyncService

open class FileDistributorFactory {
    open fun createInstance(fileSyncService: MelFileSyncService<*>): FileDistributor<*> = FileDistributorPC(fileSyncService)
}