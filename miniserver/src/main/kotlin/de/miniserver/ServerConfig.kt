package de.miniserver

import de.mein.KResult
import de.mein.sql.Pair

import java.util.HashMap

class ServerConfig : KResult {
    var certPath: String? = null
    var workingDirectory: String? = null
    var pubKeyPath: String? = null
    var certName: String? = null

    var privKeyPath: String? = null
    private val files = HashMap<String, Pair<String>>()
    var httpPort: Int = 8080

    fun getFiles(): Map<String, Pair<String>> {
        return files
    }

    fun addEntry(binaryFile: String, name: String, versionFile: String): ServerConfig {
        files[binaryFile] = Pair(String::class.java, name, versionFile)
        return this
    }
}
