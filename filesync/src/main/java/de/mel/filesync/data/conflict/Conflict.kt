package de.mel.filesync.data.conflict

import de.mel.filesync.sql.Stage
import de.mel.filesync.sql.dao.ConflictDao
import java.util.Observable

/**
 * Basic object that finds pairs of local an remote conflicts. Conflicts are based on [Stage]s of a local and a remote StageSet.
 * So it may happen that only one side has a [Stage] because it does not exist in the other.
 */
open class Conflict(val conflictDao: ConflictDao, val localStage: Stage?, val remoteStage: Stage?) {
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
    var children = mutableListOf<Conflict>()

    var chosenLocal: Boolean = false
        get() = this.hasChoice && this.decision === localStage
    var chosenRemote: Boolean = false
        get() = this.hasChoice && this.decision === remoteStage
    val hasChoice: Boolean
        get() = this.decision != null || this.rejection != null

    init {
        key = createKey(localStage, remoteStage)
        localId = localStage?.id
        remoteId = remoteStage?.id
    }

    fun dependOn(parent: Conflict?) {
        this.parent = parent
        parent?.children?.add(this)
    }

    fun addChild(child: Conflict): Conflict {
        children.add(child)
        child.parent = this
        return this
    }

    fun decideRemote(): Conflict {
        this.decision = remoteStage
        rejection = localStage
//        children.forEach { it.decideRemote() }
        return this
    }


    fun decideLocal(): Conflict {
        this.decision = localStage
        rejection = remoteStage
//        children.forEach { it.decideLocal() }
        return this
    }

    override fun toString(): String = "key: \"$key\", l: \"${localStage?.name ?: "null"}\", r: \"${
        remoteStage?.name
            ?: "null"
    }\""

    fun decideNothing(): Conflict {
        this.decision = null
        rejection = null
        children.forEach { it.decideNothing() }
        return this
    }

    fun hasLocalStage(): Boolean = this.localStage != null

    fun hasRemoteStage(): Boolean = this.remoteStage != null
    fun hasParent(): Boolean = this.parent != null
    fun hasChildren(): Boolean = !this.children.isEmpty()

    companion object {
        fun createKey(lStage: Stage?, rStage: Stage?): String =
            "${lStage?.id?.toString() ?: "n"}/${rStage?.id?.toString() ?: "n"}"

        val stage = Stage()

        fun getRootConflicts(conflicts: Collection<Conflict>): List<Conflict> {
            return conflicts.filter { !it.hasParent() }
        }

    }
}