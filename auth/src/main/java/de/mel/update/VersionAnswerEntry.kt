package de.mel.update

import de.mel.core.serialize.SerializableEntity


class VersionAnswerEntry(var variant: String?, var hash: String?, var commit: String?, var version: String?, var length: Long?, var mirrors: List<String>?) : SerializableEntity {
    constructor() : this(null, null, null, null, null, null)
}