package de.mel.drive.nio

import de.mel.auth.file.AFile
import de.mel.drive.service.MelDriveService

class FileDistributorPC(driveService:MelDriveService<*>) : FileDistributor<AFile<*>>(driveService )