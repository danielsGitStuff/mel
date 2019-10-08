package de.web.miniserver

import de.mel.KResult
import de.mel.auth.data.MelAuthSettings
import de.mel.sql.Pair
import java.io.File
import java.util.HashMap

class StaticConfig :KResult{
    var certPath: String? = null
    var workingPath: String? = DEFAULT_WORKING_DIR.absolutePath
    var pubKeyPath: String? = null
    var certName: String? = null

    var privKeyPath: String? = null
    private val files = HashMap<String, Pair<String>>()
    var httpPort: Int? = null
    var httpsPort: Int? = null
    var authPort: Int = DEFAULT_AUTH
    var transferPort: Int = DEFAULT_TRANSFER
    var pipes: Boolean = true
    var workingDirectory: File? = null
        get() = File(workingPath)
    var keySize: Int = 2048
    var restartCommand = mutableListOf<String>()
    var keepBinaries: Boolean = false

    var staticPath: String? = null


    fun getFiles(): Map<String, Pair<String>> {
        return files
    }

    fun addEntry(binaryFile: String, name: String, versionFile: String): StaticConfig {
        files[binaryFile] = Pair(String::class.java, name, versionFile)
        return this
    }

    companion object {
        val DEFAULT_WORKING_DIR = File("server")
        val DEFAULT_AUTH: Int = MelAuthSettings.UPDATE_MSG_PORT
        const val DEFAULT_TRANSFER: Int = MelAuthSettings.UPDATE_BINARY_PORT
        const val DEFAULT_HTTP: Int = 8080
        const val DEFAULT_HTTPS: Int = 8443
    }
}