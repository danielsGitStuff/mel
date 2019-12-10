package de.mel.filesync.data.conflict

import de.mel.filesync.sql.Stage
import de.mel.filesync.sql.dao.ConflictDao

class Conflict(val conflictDao: ConflictDao, val localStage: Stage?, val remoteStage: Stage?) {
    private var localId: Long?
    private var remoteId: Long?
    private var decision: Stage? = null
    var key: String
        get
    private var parent: Conflict? = null
    private var children = mutableListOf<Conflict>()
    val chosenLocal: Boolean = localStage != null && decision == localStage
    val chosenRemote: Boolean = remoteStage != null && decision == remoteStage
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
        children.forEach { it.decideRemote() }
    }

    fun decideLocal() {
        decision = localStage
        children.forEach { it.decideLocal() }
    }

    override fun toString(): String = "Class: {${javaClass.simpleName}, key: \"$key\"}"

    companion object {
        fun createKey(lStage: Stage?, rStage: Stage?): String =
                "${lStage?.id?.toString() ?: "n"}/${rStage?.id?.toString() ?: "n"}"

        val stage = Stage()

    }
}