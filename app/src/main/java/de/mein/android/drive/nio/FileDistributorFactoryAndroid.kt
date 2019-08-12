package de.mein.android.drive.nio

import de.mein.auth.file.AFile
import de.mein.drive.nio.FileDistributor
import de.mein.drive.nio.FileDistributorFactory
import de.mein.drive.service.MeinDriveService

class FileDistributorFactoryAndroid : FileDistributorFactory() {
    override fun createInstance(driveService: MeinDriveService<*>): FileDistributor<*> = FileDistributorAndroid(driveService)
}