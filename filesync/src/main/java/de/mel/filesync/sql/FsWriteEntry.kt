package de.mel.filesync.sql

import de.mel.Lok
import de.mel.sql.Pair

class FsWriteEntry(private val fsEntry: FsEntry?) : FsFile() {
    constructor() : this(FsFile())

    init {
        allAttributes.clear()
        insertAttributes = fsEntry?.insertAttributes
        allAttributes = fsEntry?.allAttributes
    }

    companion object {
        const val tableName = "fswrite"
    }

    override fun getTableName(): String = FsWriteEntry.tableName

    override fun getSynced(): Pair<Boolean> {
        return fsEntry!!.getSynced()
    }

    override fun getVersion(): Pair<Long> {
        return fsEntry!!.getVersion()
    }

    override fun getIsDirectory(): Pair<Boolean> {
        return fsEntry!!.getIsDirectory()
    }

    override fun getParentId(): Pair<Long> {
        return fsEntry!!.getParentId()
    }

    override fun getId(): Pair<Long> {
        return fsEntry!!.getId()
    }

    override fun getContentHash(): Pair<String> {
        return fsEntry!!.getContentHash()
    }

    override fun getName(): Pair<String> {
        return fsEntry!!.getName()
    }

    override fun getiNode(): Pair<Long> {
        return fsEntry!!.getiNode()
    }

    override fun getModified(): Pair<Long> {
        return fsEntry!!.getModified()
    }

    override fun getSize(): Pair<Long> {
        return fsEntry!!.getSize()
    }

    override fun getSymLink(): Pair<String> {
        return fsEntry!!.getSymLink()
    }

    override fun getCreated(): Pair<Long> {
        return fsEntry!!.getCreated()
    }
}