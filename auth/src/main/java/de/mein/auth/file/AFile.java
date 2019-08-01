package de.mein.auth.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import de.mein.Lok;

/**
 * Think of this as an abstraction of {@link java.io.File}. It is necessary cause Android 7+ restricts access to external storages via {@link java.io.File}.
 * Before using {@link AFile}, call configure() and hand over a {@link Configuration}. This determines the implementation you want to use.
 * {@link DefaultFileConfiguration} uses {@link FFile} which wraps {@link File}.
 */
public abstract class AFile<T extends  AFile> {

    private static Configuration configuration;

    public static Configuration getConfiguration() {
        return configuration;
    }

    public static AFile instance(File file) {
        return configuration.instance(file);
    }

    public abstract String getSeparator();

    public static AFile instance(AFile originalFile) {
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
     * creates common instances of {@link AFile}s
     */
    public abstract static class Configuration {
        public abstract AFile instance(String path);

        public abstract String separator();

        public abstract AFile instance(File file);

        public abstract AFile instance(AFile parent, String name);

        public abstract AFile instance(AFile originalFile);
    }

    private String path;

    public static void configure(Configuration configuration) {
        if (AFile.configuration != null) {
            Lok.error("AFile implementation has already been set!");
            return;
        } else {
            AFile.configuration = configuration;
        }
    }


    /**
     * It just creates some sort of root element.
     * @param path
     * @return
     */
    public static AFile instance(String path) {
        if (configuration == null)
            Lok.error(AFile.class.getSimpleName() + ". NOT INITIALIZED! Call configure() before!");
        AFile file = configuration.instance(path);
        return file;
    }

    public static AFile instance(AFile parent, String name) {
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

    public abstract FileInputStream inputStream() throws FileNotFoundException;

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
