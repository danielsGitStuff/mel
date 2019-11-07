package de.mel.filesync.sql

class FsWriteFile : FsFile() {
    override fun getTableName(): String = "fswrite"
    override fun newDummyInstance(): FsFile = FsWriteFile()
}