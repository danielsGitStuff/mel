package de.mel.filesync.nio

import de.mel.auth.file.AFile
import de.mel.filesync.service.MelFileSyncService

class FileDistributorPC(fileSyncService: MelFileSyncService<*>) : FileDistributor<AFile<*>>(fileSyncService )