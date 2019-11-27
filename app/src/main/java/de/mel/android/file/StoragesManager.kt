package de.mel.android.file

import android.content.Context
import de.mel.auth.file.AbstractFile.Companion.instance
import de.mel.auth.file.IFile

object StoragesManager {
    @JvmStatic
    fun getStorageFiles(context: Context): Array<IFile> {
        val externals = context.getExternalFilesDirs("external")
        // sorry for that. here is what it does:
        // '/storage/emulated/0/Android/data/de.mel.meldrive/files/external' -> '/storage/emulated/0'
        return externals.map { instance(it.absolutePath.substring(0, it.absolutePath.lastIndexOf("/Android/data"))) }
                .sortedBy { it.getName() }
                .toTypedArray()
        // the old way. for nostalgic reasons
        // AbstractFile[] result = N.arr.cast(externals, N.converter(AbstractFile.class, file -> AbstractFile.instance(file.getAbsolutePath().substring(0, file.getAbsolutePath().lastIndexOf("/Android/data")))));
        // Arrays.sort(result, (o1, o2) -> o1.name.compareTo(o2.name));
    }
}