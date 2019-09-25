package de.mel.android.drive.nio

import de.mel.auth.file.AFile
import de.mel.drive.nio.FileDistributor
import de.mel.drive.nio.FileDistributorFactory
import de.mel.drive.service.MelDriveService

class FileDistributorFactoryAndroid : FileDistributorFactory() {
    override fun createInstance(driveService: MelDriveService<*>): FileDistributor<*> = FileDistributorAndroid(driveService)
}