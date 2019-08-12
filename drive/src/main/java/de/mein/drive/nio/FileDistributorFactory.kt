package de.mein.drive.nio

import de.mein.auth.file.AFile
import de.mein.drive.service.MeinDriveService

open class FileDistributorFactory {
    open fun createInstance(driveService: MeinDriveService<*>): FileDistributor<*> = FileDistributorPC(driveService)
}