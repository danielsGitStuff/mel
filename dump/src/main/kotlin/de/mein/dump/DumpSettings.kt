package de.mein.dump

import de.mein.auth.data.JsonSettings
import de.mein.auth.file.AFile
import de.mein.core.serialize.exceptions.JsonDeserializationException
import de.mein.core.serialize.exceptions.JsonSerializationException
import de.mein.drive.data.DriveSettings
import java.io.File
import java.io.IOException

class DumpSettings : DriveSettings() {
    companion object {
        //todo abstraction
        @Throws(IOException::class, JsonDeserializationException::class, JsonSerializationException::class, IllegalAccessException::class)
        fun load(jsonFile: File?): DumpSettings {
            var dumpSettings = JsonSettings.load(jsonFile) as DumpSettings
            if (dumpSettings != null) {
                dumpSettings.setJsonFile(jsonFile)
                dumpSettings.rootDirectory.backup()
                dumpSettings.transferDirectory = AFile.instance(dumpSettings.transferDirectoryPath)
            }
            return dumpSettings
        }
    }

    override fun isServer(): Boolean {
        return role == DumpStrings.ROLE_SERVER
    }
}