package de.mel.filesync.data.conflict

import de.mel.filesync.sql.Stage
import de.mel.filesync.sql.dao.ConflictDao

class Conflict(val conflictDao: ConflictDao, val localStage: Stage?, val remoteStage: Stage?) {
    private var localId: Long?
    private var remoteId: Long?
    var decision: Stage? = null
        get

    var rejection: Stage? = null
        get
    var key: String
        get
    var parent: Conflict? = null
        get
    private var children = mutableListOf<Conflict>()

    var chosenLocal: Boolean = false
        get() = localStage != null && decision === localStage
    var chosenRemote: Boolean = false
        get() = remoteStage != null && decision === remoteStage
    val hasChoice: Boolean
        get() = decision != null

    init {
        key = createKey(localStage, remoteStage)
        localId = localStage?.id
        remoteId = remoteStage?.id
    }

    fun dependOn(parent: Conflict?) {
        this.parent = parent
        parent?.children?.add(this)
    }

    fun addChild(child: Conflict) {
        children.add(child)
        child.parent = this
    }

    fun decideRemote() {
        decision = remoteStage
        rejection = localStage
        children.forEach { it.decideRemote() }
    }

    fun decideLocal() {
        decision = localStage
        rejection = remoteStage
        children.forEach { it.decideLocal() }
    }

    override fun toString(): String = "key: \"$key\", l: \"${localStage?.name ?: "null"}\", r: \"${remoteStage?.name
            ?: "null"}\""

    companion object {
        fun createKey(lStage: Stage?, rStage: Stage?): String =
                "${lStage?.id?.toString() ?: "n"}/${rStage?.id?.toString() ?: "n"}"

        val stage = Stage()

    }
}