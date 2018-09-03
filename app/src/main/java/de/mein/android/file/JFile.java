package de.mein.android.file;

import android.net.Uri;
import android.os.Environment;

import com.archos.filecorelibrary.FileComparator;
import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.localstorage.JavaFile2;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import de.mein.auth.file.AFile;
import de.mein.auth.tools.N;

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

    public JFile(JavaFile2 javaFile2) {
        this.file = javaFile2;
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
        String path = uri.getPath();
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
        return list(File::isFile);
    }

    @Override
    public AFile[] listDirectories() {
        return list(File::isDirectory);
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
            return (FileInputStream) stream;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public FileOutputStream outputStream() throws IOException {
        try {
            FileOutputStream stream = (FileOutputStream) getFileEditor().getOutputStream();
            return stream;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;    }

    @Override
    public Long getFreeSpace() {
        File ioFile = new File(file.getUri().getEncodedPath());
        if (ioFile.exists())
            return ioFile.getFreeSpace();
        return -1L;
    }

    @Override
    public Long getUsableSpace() {
        File ioFile = new File(file.getUri().getEncodedPath());
        if (ioFile.exists())
            return ioFile.getUsableSpace();
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

    private AFile[] list(FileFilter fileFilter) {
        final ArrayList<JavaFile2> content = new ArrayList<JavaFile2>();


        File directory = new File(file.getUri().getPath());

        // File not found error
        if (!directory.exists()) {
            return new AFile[0];
        }
        if (!directory.canRead()) {
            return new AFile[0];
        }

        File[] listFiles = fileFilter == null ? directory.listFiles() : directory.listFiles(fileFilter);


        // Check Error in reading the directory (java.io.File do not allow any details about the error...).
        if (listFiles == null) {
//                postError(ListingEngine.ErrorEnum.ERROR_UNKNOWN);
            return new AFile[0];
        }

        for (File f : listFiles) {
            if (f.isDirectory()) {
                content.add(new JavaFile2(f, JavaFile2.NUMBER_UNKNOWN, JavaFile2.NUMBER_UNKNOWN));
            } else if (f.isFile()) {
                content.add(new JavaFile2(f));
            }
        }

        Collections.sort(content, Comparator.comparing(JavaFile2::getName));

        AFile[] result = N.arr.fromCollection(content, N.converter(AFile.class, element -> new JFile(element)));
        return result;
    }

    @Override
    public AFile[] listContent() {
        return list(null);
    }
}
