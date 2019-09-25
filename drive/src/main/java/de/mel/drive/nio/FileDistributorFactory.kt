package de.mel.drive.nio

import de.mel.auth.file.AFile
import de.mel.drive.service.MelDriveService

open class FileDistributorFactory {
    open fun createInstance(driveService: MelDriveService<*>): FileDistributor<*> = FileDistributorPC(driveService)
}