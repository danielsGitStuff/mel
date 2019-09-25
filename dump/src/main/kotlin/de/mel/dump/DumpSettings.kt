package de.mel.dump

import de.mel.auth.data.JsonSettings
import java.io.File

class DumpSettings : JsonSettings() {
    override fun init() {

    }

    var eliminateDoubleHashes: Boolean? = false

    companion object {
        fun load(jsonFile: File): DumpSettings {
            val dumpSettings = JsonSettings.load(jsonFile) as DumpSettings
            if (dumpSettings != null) {
                dumpSettings.setJsonFile(jsonFile)
            }
            return dumpSettings
        }
    }
}