package de.mel.update

import de.mel.core.serialize.SerializableEntity


data class VersionAnswerEntry(val variant: String, val hash: String, val commit: String, val version: String, val length: Long, val mirrors: List<String>) : SerializableEntity