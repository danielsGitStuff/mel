package de.mel.filesync.nio

import de.mel.auth.file.AbstractFile
import de.mel.filesync.service.MelFileSyncService

class FileDistributorPC(fileSyncService: MelFileSyncService<*>) : FileDistributor<AbstractFile>(fileSyncService )