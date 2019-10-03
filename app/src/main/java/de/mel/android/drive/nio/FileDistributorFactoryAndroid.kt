package de.mel.android.drive.nio

import de.mel.drive.nio.FileDistributor
import de.mel.drive.nio.FileDistributorFactory
import de.mel.drive.service.MelFileSyncService

class FileDistributorFactoryAndroid : FileDistributorFactory() {
    override fun createInstance(fileSyncService: MelFileSyncService<*>): FileDistributor<*> = FileDistributorAndroid(fileSyncService)
}