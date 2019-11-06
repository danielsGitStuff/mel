package de.mel.auth.file;

import java.io.File;

import de.mel.Lok;

public class DefaultFileConfiguration extends AbstractFile.Configuration {
    @Override
    public AbstractFile instance(String path) {
        return new StandardFile(path);
    }

    @Override
    public String separator() {
        return File.separator;
    }

    @Override
    public AbstractFile instance(File file) {
        return new StandardFile(file);
    }

    @Override
    public AbstractFile instance(AbstractFile parent, String name) {
        if (parent instanceof StandardFile) {
            StandardFile standardFile = (StandardFile) parent;
            return new StandardFile(standardFile, name);
        } else {
            Lok.error("got a '" + parent.getClass().getSimpleName() + "' as parent.");
            return null;
        }
    }

    @Override
    public AbstractFile instance(AbstractFile originalFile) {
        return new StandardFile((StandardFile) originalFile);
    }
}
