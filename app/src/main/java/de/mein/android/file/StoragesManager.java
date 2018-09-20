package de.mein.android.file;

import android.content.Context;

import java.io.File;
import java.util.Arrays;

import de.mein.auth.file.AFile;
import de.mein.auth.tools.N;

public class StoragesManager {
    public static AFile[] getStorageFiles(Context context) {
        File[] externals = context.getExternalFilesDirs("external");
        AFile[] result = N.arr.cast(externals, N.converter(AFile.class, file -> AFile.instance(file.getAbsolutePath().substring(0, file.getAbsolutePath().lastIndexOf("/Android/data")))));
        Arrays.sort(result, (o1, o2) -> o1.getName().compareTo(o2.getName()));
        return result;

    }
}
