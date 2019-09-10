package de.mein.dump

import de.mein.auth.data.JsonSettings
import java.io.File

class DumpSettings : JsonSettings() {
    override fun init() {

    }

    var eleminateDoubleHashes: Boolean? = false

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