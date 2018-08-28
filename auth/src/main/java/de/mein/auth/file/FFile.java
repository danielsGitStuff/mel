package de.mein.auth.file;

import de.mein.auth.tools.N;

import java.io.*;

/**
 * this is the default {@link File} wrapper for everything that is not recent android
 */
public class FFile extends AFile {
    private File file;

    public FFile() {

    }

    public FFile(FFile parent, String name) {
        file = new File(parent.file, name);
    }

    public FFile(FFile originalFile) {
        this.file = new File(originalFile.file.getAbsolutePath());
    }

    @Override
    public String toString() {
        return file.toString();
    }

    public FFile(File parent, String name) {
        this.file = new File(parent, name);
    }

    public FFile(File file) {
        this.file = file;
    }

    public FFile(String path) {
        this(new File(path));
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
    public boolean move(AFile target) {
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
        return N.arr.cast(file.listFiles(File::isFile), N.converter(FFile.class, FFile::new));

    }

    @Override
    public AFile[] listDirectories() {
        return N.arr.cast(file.listFiles(File::isDirectory), N.converter(FFile.class, FFile::new));

    }


    @Override
    public boolean delete() {
        return file.delete();
    }

    @Override
    public AFile getParentFile() {
        return new FFile(file.getParentFile());
    }

    @Override
    public boolean mkdirs() {
        return file.mkdirs();
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
    public String getSeparator() {
        return File.separator;
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

    @Override
    public AFile[] listContent() {
        return N.arr.cast(file.listFiles(), N.converter(FFile.class, FFile::new));
    }
}
