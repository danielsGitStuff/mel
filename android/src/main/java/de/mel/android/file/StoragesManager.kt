package de.mel.android.file

import android.content.Context
import java.io.File

object StoragesManager {
    @JvmStatic
    fun getStorageFiles(context: Context): Array<File> {
        val externals = context.getExternalFilesDirs("external")
        return externals.filterNotNull().map { File(it.absolutePath.substring(0, it.absolutePath.lastIndexOf("/Android/data"))) }
                .sortedBy { it.name }
                .toTypedArray()
    }
}
