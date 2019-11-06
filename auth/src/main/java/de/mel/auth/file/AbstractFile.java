package de.mel.auth.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import de.mel.Lok;

/**
 * Think of this as an abstraction of {@link java.io.File}. It is necessary cause Android 7+ restricts access to external storages via {@link java.io.File}.
 * Before using {@link AbstractFile}, call configure() and hand over a {@link Configuration}. This determines the implementation you want to use.
 * {@link DefaultFileConfiguration} uses {@link StandardFile} which wraps {@link File}.
 */
public abstract class AbstractFile<T extends AbstractFile> {

    private static Configuration configuration;

    public static Configuration getConfiguration() {
        return configuration;
    }

    public static AbstractFile instance(File file) {
        return configuration.instance(file);
    }

    public abstract String getSeparator();

    public static AbstractFile instance(AbstractFile originalFile) {
        return configuration.instance(originalFile);
    }

    /**
     *
     * @param subFile
     * @return true if subFile is located in a subfolder of this instance.
     */
    public abstract boolean hasSubContent(T subFile);

    public abstract String getCanonicalPath() throws IOException;


    /**
     * creates common instances of {@link AbstractFile}s
     */
    public abstract static class Configuration {
        public abstract AbstractFile instance(String path);

        public abstract String separator();

        public abstract AbstractFile instance(File file);

        public abstract AbstractFile instance(AbstractFile parent, String name);

        public abstract AbstractFile instance(AbstractFile originalFile);
    }

    private String path;

    public static void configure(Configuration configuration) {
        if (AbstractFile.configuration != null) {
            Lok.error("AFile implementation has already been set!");
            return;
        } else {
            AbstractFile.configuration = configuration;
        }
    }


    /**
     * It just creates some sort of root element.
     * @param path
     * @return
     */
    public static AbstractFile instance(String path) {
        if (configuration == null)
            Lok.error(AbstractFile.class.getSimpleName() + ". NOT INITIALIZED! Call configure() before!");
        AbstractFile file = configuration.instance(path);
        return file;
    }

    public static AbstractFile instance(AbstractFile parent, String name) {
        return configuration.instance(parent,name);
    }


    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public abstract String getName();

    public abstract String getAbsolutePath();

    public abstract boolean exists();

    public abstract boolean isFile();

//    public abstract boolean move(T target);

    public abstract boolean isDirectory();

    public abstract Long length();

    public abstract T[] listFiles();

    public abstract T[] listDirectories();

    public abstract boolean delete();

    public abstract T getParentFile();

    public abstract boolean mkdirs();

    public abstract InputStream inputStream() throws FileNotFoundException;

    public abstract FileOutputStream outputStream() throws IOException;

    public abstract Long getFreeSpace();

    public abstract Long getUsableSpace();

    public abstract Long lastModified();

    public static String separator() {
        return configuration.separator();
    }

    public abstract boolean createNewFile() throws IOException;

    public abstract T[] listContent();
}
