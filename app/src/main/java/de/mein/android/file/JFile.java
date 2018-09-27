package de.mein.android.file;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

import de.mein.Lok;
import de.mein.android.Tools;
import de.mein.android.service.CopyService;
import de.mein.auth.file.AFile;
import de.mein.auth.tools.N;

public class JFile extends AFile<JFile> {

    private File file;
    private JFile parentFile;

    public JFile(String path) {
        if (path == null)
            Lok.debug("JFile.JFile.debu.null");
        file = new File(path);
    }

    public JFile(File file) {
        this.file = file;
    }

    public JFile(JFile parent, String name) {
        this.parentFile = parent;
        this.file = new File(parent.getAbsolutePath() + File.separator + name);
    }

    public JFile(JFile originalFile) {
        this.file = new File(originalFile.getAbsolutePath());
    }


    private AndroidFileConfiguration getAndroidConfiguration() {
        return (AndroidFileConfiguration) AFile.getConfiguration();
    }


    @Override
    public String getSeparator() {
        return File.separator;
    }

    @Override
    public boolean hasSubContent(JFile subFile) {
        if (subFile != null)
            return subFile.file.getAbsolutePath().startsWith(file.getAbsolutePath());
        return false;
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
    public boolean equals(Object obj) {
        if (obj != null && obj instanceof JFile) {
            JFile jFile = (JFile) obj;
            return jFile.file.equals(file);
        }
        return false;
    }

    @Override
    public boolean move(JFile target) {
        try {
            Intent copyIntent = new Intent(Tools.getApplicationContext(), CopyService.class);
            copyIntent.putExtra(CopyService.SRC_PATH, file.getAbsolutePath());
            copyIntent.putExtra(CopyService.TRGT_PATH, target.file.getAbsolutePath());
            copyIntent.putExtra(CopyService.MOVE, true);
            Tools.getApplicationContext().startService(copyIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
    public JFile[] listFiles() {
        return list(File::isFile);
    }

    @Override
    public JFile[] listDirectories() {
        return list(File::isDirectory);
    }


    @Override
    public boolean delete() {
        if (requiresSAF()) {
            try {
                DocumentFile documentFile = DocFileCreator.createDocFile(file);
                if (documentFile != null)
                    return documentFile.delete();
            } catch (SAFAccessor.SAFException e) {
                e.printStackTrace();
            }
        } else {
            return file.delete();
        }
        return true;
    }


    @Override
    public JFile getParentFile() {
        return new JFile(file.getParentFile());
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private boolean mkdir() {
        try {
            DocumentFile folderDoc = DocFileCreator.createParentDocFile(file);
            String name = file.getName();
            DocumentFile found = folderDoc.findFile(name);
            if (found != null) {
                return false;
            }
            DocumentFile created = folderDoc.createDirectory(name);
            return created != null && created.exists();
        } catch (SAFAccessor.SAFException e) {
            e.printStackTrace();
        }
        return false;
    }


    @Override
    public boolean mkdirs() {
        if (requiresSAF()) {
            if (parentFile != null)
                if (!parentFile.exists()) {
                    boolean made = parentFile.mkdirs();
                    if (!made)
                        return false;
                }
            return mkdir();
        } else {
            return file.mkdirs();
        }
    }

    @Override
    public FileInputStream inputStream() throws FileNotFoundException {
//        try {
//            InputStream stream = getFileEditor().getInputStream();
//            return (FileInputStream) stream;
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        return new FileInputStream(file);
    }

    @Override
    public FileOutputStream outputStream() throws IOException {
        try {
            if (requiresSAF()) {
                DocumentFile documentFile = DocFileCreator.createDocFile(file);
                if (documentFile == null) {
                    DocumentFile parent = DocFileCreator.createParentDocFile(file);
                    documentFile = parent.createFile(SAFAccessor.MIME_GENERIC, file.getName());
                }
                FileOutputStream outputStream = (FileOutputStream) Tools.getApplicationContext().getContentResolver().openOutputStream(documentFile.getUri());
                return outputStream;
            } else {
                return new FileOutputStream(file);
            }
        } catch (SAFAccessor.SAFException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Long getFreeSpace() {
//        File ioFile = new File(file.getUri().getEncodedPath());
//        if (ioFile.exists())
//            return ioFile.getFreeSpace();
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

    private boolean requiresSAF() {
        String internalPath = Environment.getDataDirectory().getAbsolutePath();
        if (file.getAbsolutePath().startsWith(internalPath))
            return false;
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT;
    }

    public static class DocFileCreator {

        private static DocumentFile createDoc(String[] parts) throws SAFAccessor.SAFException {
            DocumentFile doc = SAFAccessor.getExternalRootDocFile();
            for (String part : parts) {
                doc = doc.findFile(part);
            }
            return doc;
        }

        private static String[] createRelativeFilePathParts(File file) {
            String rootPath = SAFAccessor.getExternalSDPath();
            String path = file.getAbsolutePath();
            String stripped = path.substring(rootPath.length());
            return stripped.split(File.separator);
        }

        public static DocumentFile createParentDocFile(File file) throws SAFAccessor.SAFException {
            String[] parts = createRelativeFilePathParts(file);
            parts = Arrays.copyOf(parts, parts.length - 1);
            return createDoc(parts);
        }

        public static DocumentFile createDocFile(File file) throws SAFAccessor.SAFException {
            String[] parts = createRelativeFilePathParts(file);
            return createDoc(parts);
        }
    }


    @Override
    public boolean createNewFile() throws IOException {
        if (requiresSAF()) {
            try {
                DocumentFile folderDoc = DocFileCreator.createParentDocFile(file);
                DocumentFile found = folderDoc.findFile(file.getName());
                if (found != null) {
                    return false;
                }
                DocumentFile created = folderDoc.createFile(SAFAccessor.MIME_GENERIC, file.getName());
                if (created != null) {
                    return true;
                }
                return false;
            } catch (SAFAccessor.SAFException e) {
                e.printStackTrace();
            }
        } else {
            return file.createNewFile();
        }
        return false;
    }

    private JFile[] list(FileFilter fileFilter) {

        File directory = new File(file.getAbsolutePath());

        // File not found error
        if (!directory.exists()) {
            return new JFile[0];
        }
        if (!directory.canRead()) {
            return new JFile[0];
        }

        File[] listFiles = fileFilter == null ? directory.listFiles() : directory.listFiles(fileFilter);


        // Check Error in reading the directory (java.io.File do not allow any details about the error...).
        if (listFiles == null) {
//                postError(ListingEngine.ErrorEnum.ERROR_UNKNOWN);
            return new JFile[0];
        }

        Arrays.sort(listFiles, (o1, o2) -> o1.getName().compareTo(o2.getName()));

        JFile[] result = N.arr.cast(listFiles, N.converter(JFile.class, JFile::new));
        return result;
    }

    @Override
    public JFile[] listContent() {
        return list(null);
    }

}
