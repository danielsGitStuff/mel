package de.mel.drive.nio

import de.mel.drive.service.MelFileSyncService

open class FileDistributorFactory {
    open fun createInstance(fileSyncService: MelFileSyncService<*>): FileDistributor<*> = FileDistributorPC(fileSyncService)
}