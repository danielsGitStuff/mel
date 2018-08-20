package de.mein.drive.nio;

import java.io.File;

import de.mein.auth.file.AFile;
import de.mein.auth.file.FFile;

public class DefaultFileConfiguration extends AFile.Configuration {
    @Override
    public AFile instance(String path) {
        return new FFile(path);
    }

    @Override
    public String separator() {
        return File.separator;
    }

    @Override
    public AFile instance(File file) {
        return new FFile(file);
    }

    @Override
    public AFile instance(AFile parent, String name) {
        if (parent instanceof FFile) {
            FFile fFile = (FFile) parent;
            return new FFile(fFile, name);
        } else {
            System.err.println(getClass().getSimpleName() + "instance(AFile parent, String name), got a '" + parent.getClass().getSimpleName() + "' as parent.");
            return null;
        }
    }
}
