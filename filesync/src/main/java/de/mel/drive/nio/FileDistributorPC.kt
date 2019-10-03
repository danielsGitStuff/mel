package de.mel.drive.nio

import de.mel.auth.file.AFile
import de.mel.drive.service.MelFileSyncService

class FileDistributorPC(fileSyncService: MelFileSyncService<*>) : FileDistributor<AFile<*>>(fileSyncService )