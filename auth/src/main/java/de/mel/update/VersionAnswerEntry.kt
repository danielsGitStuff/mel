package de.mel.update


data class VersionAnswerEntry(val variant: String, val hash: String, val commit: String, val version: String, val length: Long, val mirrors: List<String>)