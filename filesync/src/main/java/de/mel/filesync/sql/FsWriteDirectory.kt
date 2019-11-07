package de.mel.filesync.sql

class FsWriteDirectory : FsDirectory() {
    override fun getTableName(): String = "fswrite"
    override fun newDummyInstance(): FsDirectory = FsWriteDirectory()
}