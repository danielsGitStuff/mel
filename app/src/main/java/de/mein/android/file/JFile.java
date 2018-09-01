package de.mein.android.file;

import android.net.Uri;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.localstorage.JavaFile2;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import de.mein.auth.file.AFile;

public class JFile extends AFile {

    private JavaFile2 file;
    private JFile parentFile;

    public JFile(String path) {
        file = new JavaFile2(new File(path));
    }

    public JFile(File file) {
        this.file = new JavaFile2(file);
    }

    public JFile(JFile parent, String name) {
        this.parentFile = parent;
        this.file = new JavaFile2(new File(parent.getAbsolutePath() + File.separator + name));
    }

    public JFile(JFile originalFile) {
        this.file = new JavaFile2(new File(originalFile.getAbsolutePath()));
    }

    private AndroidFileConfiguration getAndroidConfiguration() {
        return (AndroidFileConfiguration) AFile.getConfiguration();
    }

    private FileEditor getFileEditor() {
        return file.getFileEditorInstance(getAndroidConfiguration().getContext());
    }

    @Override
    public String getSeparator() {
        return File.separator;
    }

    @Override
    public String getName() {
        return file.getName();
    }

    @Override
    public String getAbsolutePath() {
        Uri uri = file.getUri();
        String path = uri.getEncodedPath();
        return path;
    }

    @Override
    public boolean exists() {
        return getFileEditor().exists();
    }

    @Override
    public boolean isFile() {
        return file.isFile();
    }

    @Override
    public boolean move(AFile target) {
        return false;
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
        return new AFile[0];
    }

    @Override
    public AFile[] listDirectories() {
        return new AFile[0];
    }

    @Override
    public boolean delete() {
        try {
            getFileEditor().delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public AFile getParentFile() {
        return parentFile;
    }

    @Override
    public boolean mkdirs() {
        if (parentFile != null)
            if (!parentFile.exists()) {
                boolean made = parentFile.mkdirs();
                if (!made)
                    return false;
            }
        return getFileEditor().mkdir();
    }

    @Override
    public FileInputStream inputStream() throws FileNotFoundException {
        try {
            InputStream stream = getFileEditor().getInputStream();
            System.out.println("JFile.inputStream");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public FileOutputStream outputStream() throws IOException {
        return null;
    }

    @Override
    public Long getFreeSpace() {
        return -1L;
    }

    @Override
    public Long getUsableSpace() {
        return -1L;
    }

    @Override
    public Long lastModified() {
        return file.lastModified();
    }

    @Override
    public boolean createNewFile() throws IOException {
        return getFileEditor().touchFile();
    }

    @Override
    public AFile[] listContent() {
        return new AFile[0];
    }
}
