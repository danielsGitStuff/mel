package de.mein.drive.nio;


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import de.mein.auth.file.AFile;

public class DFile extends   AFile {


    @Override
    protected AFile createSubFile(String name) {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public String getAbsolutePath() {
        return null;
    }

    @Override
    public boolean exists() {
        return false;
    }

    @Override
    public boolean isFile() {
        return false;
    }

    @Override
    public boolean renameTo(AFile target) {
        return false;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public Long length() {
        return null;
    }

    @Override
    public AFile[] listFiles() {
        return new AFile[0];
    }

    @Override
    public AFile[] listDirectories() {
        return new AFile[0];
    }

    @Override
    public void delete() {

    }

    @Override
    public AFile getParentFile() {
        return null;
    }

    @Override
    public void mkdirs() {

    }

    @Override
    public FileInputStream inputStream() throws FileNotFoundException {
        return null;
    }

    @Override
    public FileOutputStream outputStream() throws IOException {
        return null;
    }

    @Override
    public Long getFreeSpace() {
        return null;
    }

    @Override
    public Long getUsableSpace() {
        return null;
    }

    @Override
    public Long lastModified() {
        return null;
    }

    @Override
    public boolean createNewFile() throws IOException {
        return false;
    }
}
