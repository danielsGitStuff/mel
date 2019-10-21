package de.mel.update

import de.mel.core.serialize.SerializableEntity
import java.util.*

class VersionAnswer : SerializableEntity {
    private val entries: MutableMap<String, VersionAnswerEntry> = HashMap()

    fun addEntry(entry: VersionAnswerEntry) {
        entries[entry.variant!!] = entry
    }

    fun getHash(variant: String): String? {
        return if (entries.containsKey(variant)) entries[variant]!!.hash else null
    }

    fun getEntry(variant: String): VersionAnswerEntry? {
        return entries[variant]
    }

}