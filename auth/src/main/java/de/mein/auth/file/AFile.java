package de.mein.auth.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Think of this as an abstraction of {@link java.io.File}. It is necessary cause Android 7+ restricts access to external storages via {@link java.io.File}.
 */
public abstract class AFile {
    private static Class<? extends AFile> clazz;
    private String path;

    public static void setClass(Class<? extends AFile> clazz) {
        if (AFile.clazz == null)
            AFile.clazz = clazz;
        else
            System.err.println("AFile implementation has already been set!");
    }

    public static AFile instance(String path) {
        try {
            AFile file = clazz.newInstance();
            file.setPath(path);
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static AFile instance(AFile parent, String name) {
        return parent.createSubFile(name);
    }

    protected abstract AFile createSubFile(String name);

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

    public abstract boolean renameTo(AFile target);

    public abstract boolean isDirectory();

    public abstract Long length();

    public abstract AFile[] listFiles();

    public abstract AFile[] listDirectories();

    public abstract void delete();

    public abstract AFile getParentFile();

    public abstract void mkdirs();

    public abstract FileInputStream inputStream() throws FileNotFoundException;

    public abstract FileOutputStream outputStream() throws IOException;

    public abstract Long getFreeSpace();

    public abstract Long getUsableSpace();

    public abstract Long lastModified();

    public static String separator() {
        return File.separator;
    }

    public abstract boolean createNewFile() throws IOException;
}
