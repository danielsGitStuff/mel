package de.mel.android.file;

import android.content.Context;

import java.io.File;

import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;

public class AndroidFileConfiguration extends AbstractFile.Configuration<AndroidFile> {
    private static File dataDir;
    private Context context;

    public static File getDataDir() {
        return dataDir;
    }

    public AndroidFileConfiguration(Context context) {
        this.context = context;
        AndroidFileConfiguration.dataDir = context.getFilesDir().getParentFile();
    }

    @Override
    public AndroidFile instance(String path) {
        return new AndroidFile(path);
    }

    @Override
    public String separator() {
        return "%2F";
    }

    @Override
    public AndroidFile instance(File file) {
        return new AndroidFile(file);
    }


    @Override
    public AndroidFile instance(AndroidFile parent, String name) {
        if (parent instanceof AndroidFile)
            return new AndroidFile((AndroidFile) parent, name);
        else
            System.err.println(getClass().getSimpleName() + "instance(AFile parent, String name), got a '" + parent.getClass().getSimpleName() + "' as parent.");
        return null;
    }

    @Override
    public AndroidFile instance(AndroidFile originalFile) {
        return new AndroidFile((AndroidFile) originalFile);

    }

    public Context getContext() {
        return context;
    }
}
