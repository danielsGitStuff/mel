package de.mein.android.file;

import android.net.Uri;

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
        final ArrayList<JavaFile2> directories = new ArrayList<JavaFile2>();


        File directory = new File(file.getUri().getPath());

        // File not found error
        if (!directory.exists()) {
            return new AFile[0];
        }
        if (!directory.canRead()) {
            return new AFile[0];
        }

        File[] listFiles = directory.listFiles(File::isDirectory);


        // Check Error in reading the directory (java.io.File do not allow any details about the error...).
        if (listFiles == null) {
//                postError(ListingEngine.ErrorEnum.ERROR_UNKNOWN);
            return new AFile[0];
        }

        final ArrayList<JavaFile2> files = new ArrayList<JavaFile2>();
        for (File f : listFiles) {
            if (f.isDirectory()) {
                directories.add(new JavaFile2(f, JavaFile2.NUMBER_UNKNOWN, JavaFile2.NUMBER_UNKNOWN));
            } else if (f.isFile()) {
                files.add(new JavaFile2(f));
            }
        }

        // Put directories first, then files
//            final Comparator<? super JavaFile2> comparator = new FileComparator().selectFileComparator(mSortOrder);
//            Collections.sort(directories, comparator);
//            Collections.sort(files, comparator);
        final ArrayList<JavaFile2> allFiles = new ArrayList<JavaFile2>(directories.size() + files.size());
        allFiles.addAll(directories);
        allFiles.addAll(files);


        for (final File f : listFiles) {
            if (f.isDirectory()) {
                // Count the files and folders inside this folder
                int numerOfDirectories = 0;
                int numberOfFiles = 0;
                File[] insideFiles = f.listFiles();
                if (insideFiles != null) {
                    for (File insideFile : insideFiles) {
                        if (insideFile.isDirectory()) {
                            numerOfDirectories++;
                        } else if (insideFile.isFile()) {
                            numberOfFiles++;
                        }
                    }
                }

                final JavaFile2 javaFile2 = new JavaFile2(f, numberOfFiles, numerOfDirectories);

            }

        }
        AFile[] result = N.arr.fromCollection(directories, N.converter(AFile.class, element -> new JFile(element)));
        return result;
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
