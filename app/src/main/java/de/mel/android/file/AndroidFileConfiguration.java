package de.mel.android.file;

import android.content.Context;

import java.io.File;

import de.mel.auth.file.AbstractFile;

public class AndroidFileConfiguration extends AbstractFile.Configuration {
    private Context context;

    public AndroidFileConfiguration(Context context) {
        this.context = context;
    }

    @Override
    public AbstractFile instance(String path) {
        return new AndroidFile(path);
    }

    @Override
    public String separator() {
        return "%2F";
    }

    @Override
    public AbstractFile instance(File file) {
        return new AndroidFile(file);
    }


    @Override
    public AbstractFile instance(AbstractFile parent, String name) {
        if (parent instanceof AndroidFile)
            return new AndroidFile((AndroidFile) parent, name);
        else
            System.err.println(getClass().getSimpleName() + "instance(AFile parent, String name), got a '" + parent.getClass().getSimpleName() + "' as parent.");
        return null;
    }

    @Override
    public AbstractFile instance(AbstractFile originalFile) {
        return new AndroidFile((AndroidFile) originalFile);

    }

    public Context getContext() {
        return context;
    }
}
