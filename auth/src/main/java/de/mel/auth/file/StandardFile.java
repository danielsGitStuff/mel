package de.mel.auth.file;

import de.mel.auth.tools.N;

import java.io.*;

/**
 * this is the default {@link File} wrapper for everything that is not recent android
 */
public class StandardFile extends AbstractFile<StandardFile> {
    private File file;

    public File getFile() {
        return file;
    }

    public StandardFile() {

    }

    @Override
    public String getPath() {
        return file.getPath();
    }

    public StandardFile(StandardFile parent, String name) {
        file = new File(parent.file, name);
    }

    public StandardFile(StandardFile originalFile) {
        this.file = new File(originalFile.file.getAbsolutePath());
    }

    @Override
    public String toString() {
        return file.toString();
    }

    public StandardFile(File parent, String name) {
        this.file = new File(parent, name);
    }

    public StandardFile(File file) {
        this.file = file;
    }

    public StandardFile(String path) {
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

//    @Override
//    public boolean move(FFile target) {
//        return file.renameTo(new File(target.getAbsolutePath()));
//    }

    @Override
    public boolean isDirectory() {
        return file.isDirectory();
    }

    @Override
    public Long length() {
        return file.length();
    }

    @Override
    public StandardFile[] listFiles() {
        return N.arr.cast(file.listFiles(File::isFile), N.converter(StandardFile.class, StandardFile::new));

    }

    @Override
    public StandardFile[] listDirectories() {
        return N.arr.cast(file.listFiles(File::isDirectory), N.converter(StandardFile.class, StandardFile::new));

    }


    @Override
    public boolean delete() {
        return file.delete();
    }

    @Override
    public StandardFile getParentFile() {
        return new StandardFile(new File(file.getAbsolutePath()).getParentFile());
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
    public boolean hasSubContent(StandardFile subFile) {
        return subFile != null && subFile.getAbsolutePath().startsWith(file.getAbsolutePath());
    }

    @Override
    public String getCanonicalPath() throws IOException {
        return file.getCanonicalPath();
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
    public StandardFile[] listContent() {
        return N.arr.cast(file.listFiles(), N.converter(StandardFile.class, StandardFile::new));
    }
}
