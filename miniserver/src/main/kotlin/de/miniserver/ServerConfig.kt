package de.miniserver

import de.mein.KResult
import de.mein.auth.data.MeinAuthSettings
import de.mein.sql.Pair
import java.util.*

class ServerConfig : KResult {
    var certPath: String? = null
    var workingDirectory: String? = null
    var pubKeyPath: String? = null
    var certName: String? = null

    var privKeyPath: String? = null
    private val files = HashMap<String, Pair<String>>()
    var httpPort: Int? = null
    var authPort: Int = DEFAULT_AUTH
    var transferPort: Int = DEFAULT_TRANSFER


    fun getFiles(): Map<String, Pair<String>> {
        return files
    }

    fun addEntry(binaryFile: String, name: String, versionFile: String): ServerConfig {
        files[binaryFile] = Pair(String::class.java, name, versionFile)
        return this
    }

    companion object {
        val DEFAULT_AUTH: Int = MeinAuthSettings.UPDATE_MSG_PORT
        const val DEFAULT_TRANSFER: Int = MeinAuthSettings.UPDATE_BINARY_PORT
        const val DEFAULT_HTTP: Int = 8450
    }
}
