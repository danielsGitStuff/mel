package de.mel.android.filesync.nio

import de.mel.android.file.SAFFile
import de.mel.filesync.nio.FileDistributor
import de.mel.filesync.service.MelFileSyncService

class SAFFileDistributor(fileSyncService: MelFileSyncService<*>) : FileDistributor<SAFFile>(fileSyncService) {

    override fun moveFile(sourceFile: SAFFile, lastFile: SAFFile, lastPath: String, lastId: Long?) {

    }
}