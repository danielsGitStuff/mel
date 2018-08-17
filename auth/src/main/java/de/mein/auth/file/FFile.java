package de.mein.auth.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import de.mein.auth.tools.N;

/**
 * this is the default {@link File} wrapper for everything that is not recent android
 */
public class FFile extends AFile {
    private File file;

    public FFile() {

    }

    public FFile(File parent, String name) {
        this.file = new File(parent, name);
    }

    public FFile(File file) {
        this.file = file;
    }


    @Override
    protected AFile createSubFile(String name) {
        return new FFile(file, name);
    }

    @Override
    public void setPath(String path) {
        file = new File(path);
    }

    @Override
    public String getName() {
        return file.getName();
    }

    @Override
    public String getAbsolutePath() {
        return file.getAbsolutePath();
    }

    @Override
    public boolean exists() {
        return file.exists();
    }

    @Override
    public boolean isFile() {
        return file.isFile();
    }

    @Override
    public boolean renameTo(AFile target) {
        return file.renameTo(new File(target.getAbsolutePath()));
    }

    @Override
    public boolean isDirectory() {
        return file.isDirectory();
    }

    @Override
    public Long length() {
        return file.length();
    }

    @Override
    public AFile[] listFiles() {
        File[] files = file.listFiles(File::isFile);
        return N.arr.cast(files, element -> AFile.instance(element.getAbsolutePath()));
    }

    @Override
    public AFile[] listDirectories() {
        return N.arr.cast(file.listFiles(File::isDirectory), element -> AFile.instance(element.getAbsolutePath()));
    }

    @Override
    public void delete() {
        file.delete();
    }

    @Override
    public AFile getParentFile() {
        return new FFile(file.getParentFile());
    }

    @Override
    public void mkdirs() {
        file.mkdirs();
    }

    @Override
    public FileInputStream inputStream() throws FileNotFoundException {
        return new FileInputStream(file);
    }

    @Override
    public FileOutputStream outputStream() throws FileNotFoundException {
        return new FileOutputStream(file);
    }

    @Override
    public Long getFreeSpace() {
        return file.getFreeSpace();
    }

    @Override
    public Long getUsableSpace() {
        return file.getUsableSpace();
    }

    @Override
    public Long lastModified() {
        return file.lastModified();
    }

    @Override
    public boolean createNewFile() throws IOException {
        return file.createNewFile();
    }
}
