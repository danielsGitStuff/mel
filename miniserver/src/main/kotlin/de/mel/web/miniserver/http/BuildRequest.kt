package de.mel.web.miniserver.http

import de.mel.core.serialize.SerializableEntity

class BuildRequest() : SerializableEntity {
    var blog: Boolean? = null
    var server: Boolean? = null
    var apk: Boolean? = null
    var jar: Boolean? = null
    var pw: String? = null
    var keepBinaries: Boolean? = null
    var release: Boolean? = null
    val valid: Boolean
        get() {
            return server != null && apk != null && jar != null && blog != null && keepBinaries != null && release != null
        }
}