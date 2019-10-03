package de.mel.android.drive.nio

import de.mel.filesync.nio.FileDistributor
import de.mel.filesync.nio.FileDistributorFactory
import de.mel.filesync.service.MelFileSyncService

class FileDistributorFactoryAndroid : FileDistributorFactory() {
    override fun createInstance(fileSyncService: MelFileSyncService<*>): FileDistributor<*> = FileDistributorAndroid(fileSyncService)
}