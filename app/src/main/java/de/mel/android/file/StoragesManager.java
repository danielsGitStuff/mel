package de.mel.android.file;

import android.content.Context;

import java.io.File;
import java.util.Arrays;

import de.mel.auth.file.AbstractFile;
import de.mel.auth.tools.N;

public class StoragesManager {
    public static AbstractFile[] getStorageFiles(Context context) {
        File[] externals = context.getExternalFilesDirs("external");
        // sorry for that. here is what it does:
        // '/storage/emulated/0/Android/data/de.mel.meldrive/files/external' -> '/storage/emulated/0'
        AbstractFile[] result = N.arr.cast(externals, N.converter(AbstractFile.class, file -> AbstractFile.instance(file.getAbsolutePath().substring(0, file.getAbsolutePath().lastIndexOf("/Android/data")))));
        Arrays.sort(result, (o1, o2) -> o1.name.compareTo(o2.name));
        return result;

    }
}
