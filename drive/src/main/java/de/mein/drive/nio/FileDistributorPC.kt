package de.mein.drive.nio

import de.mein.auth.file.AFile
import de.mein.drive.service.MeinDriveService

class FileDistributorPC(driveService:MeinDriveService<*>) : FileDistributor<AFile<*>>(driveService )