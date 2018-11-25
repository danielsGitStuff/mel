package de.miniserver

import de.mein.KResult
import de.mein.auth.data.MeinAuthSettings
import de.mein.sql.Pair
import java.io.File
import java.util.*

class ServerConfig : KResult {
    var certPath: String? = null
    var workingPath: String? = DEFAULT_WORKING_DIR.absolutePath
    var pubKeyPath: String? = null
    var certName: String? = null

    var privKeyPath: String? = null
    private val files = HashMap<String, Pair<String>>()
    var httpPort: Int? = null
    var authPort: Int = DEFAULT_AUTH
    var transferPort: Int = DEFAULT_TRANSFER
    var pipes: Boolean = false
    var workingDirectory: File? = null
        get() = File(workingPath)
    var keySize: Int = 2048


    fun getFiles(): Map<String, Pair<String>> {
        return files
    }

    fun addEntry(binaryFile: String, name: String, versionFile: String): ServerConfig {
        files[binaryFile] = Pair(String::class.java, name, versionFile)
        return this
    }

    companion object {
        val DEFAULT_WORKING_DIR = File("server")
        val DEFAULT_AUTH: Int = MeinAuthSettings.UPDATE_MSG_PORT
        const val DEFAULT_TRANSFER: Int = MeinAuthSettings.UPDATE_BINARY_PORT
        const val DEFAULT_HTTP: Int = 8450
    }
}
