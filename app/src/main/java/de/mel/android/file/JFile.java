package de.mel.android.file;

import android.annotation.TargetApi;
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

import de.mel.Lok;
import de.mel.android.Tools;
import de.mel.auth.file.AFile;
import de.mel.auth.tools.N;

public class JFile extends AFile<JFile> {

    private File file;
    private JFile parentFile;
    private boolean isExternal;
    private DocFileCache internalCache;
    private DocFileCache externalCache;

    public JFile(String path) {
        if (path == null)
            Lok.debug("JFile.JFile.debu.null");
        file = new File(path);
        init();
    }

    public JFile(File file) {
        this.file = file;
        init();
    }

    public JFile(JFile parent, String name) {
        this.parentFile = parent;
        this.file = new File(parent.getAbsolutePath() + File.separator + name);
        init();
    }

    public JFile(JFile originalFile) {
        this.file = new File(originalFile.getAbsolutePath());
        init();
    }

    private void init() {
        isExternal = SAFAccessor.isExternalFile(this);
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
    public String getCanonicalPath() throws IOException {
        return this.file.getCanonicalPath();
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
//    public boolean move(JFile target) {
//        try {
//            Intent copyIntent = new Intent(Tools.getApplicationContext(), CopyService.class);
//            copyIntent.putExtra(CopyService.SRC_PATH, file.getAbsolutePath());
//            copyIntent.putExtra(CopyService.TRGT_PATH, target.file.getAbsolutePath());
//            copyIntent.putExtra(CopyService.MOVE, true);
//            Tools.getApplicationContext().startService(copyIntent);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return false;
//    }

    @Override
    public boolean equals(Object obj) {
        if (obj != null && obj instanceof JFile) {
            JFile jFile = (JFile) obj;
            return jFile.file.equals(file);
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
                DocumentFile documentFile = createDocFile();
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
            DocumentFile folderDoc = createParentDocFile();
            String name = file.getName();
            DocumentFile found = folderDoc.findFile(name);
            if (found != null && found.isFile()) {
                found.delete();
            }
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
                DocumentFile documentFile = createDocFile();
                if (documentFile == null) {
                    DocumentFile parent = createParentDocFile();
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

    /**
     * Checks whether or not you have to employ the Storage Access Framework to write to that location.
     *
     * @return
     */
    private boolean requiresSAF() {
        String internalDataPath = Environment.getDataDirectory().getAbsolutePath();
        // no external sd card available
        if (!SAFAccessor.hasExternalSdCard())
            return false;
        // file is in data directory
        if (file.getAbsolutePath().startsWith(internalDataPath))
            return false;
        // file is not on external sd card
        if (!file.getAbsolutePath().startsWith(SAFAccessor.getExternalSDPath()))
            return false;
        // SAF is only available from kitkat onwards
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT;
    }

    private String[] createRelativeFilePathParts(String storagePath, File file) {
//        String storagePath;
//        if (isExternal) {
//            storagePath = SAFAccessor.getExternalSDPath();
//        } else {
//            storagePath = Environment.getExternalStorageDirectory().getAbsolutePath();
//        }
        String path = file.getAbsolutePath();
        // if rootPath is null, there is no external sd card.
        // if it isn't the share still might be on the internal "sd card"
        // todo test, because it is untested cause I go to bed now
        String stripped;
        if (storagePath == null) {
            Lok.error("STORAGE PATH WAS NULL");
            throw new NullPointerException("storage path was null");
        }
        if (!path.startsWith(storagePath))
            Lok.error("INVALID PATH! tried to find [" + path + "] in [" + storagePath + "]");
        // +1 to get rid of the leading slash
        int offset = 0;
        if (!path.endsWith("/"))
            offset = 1;
        stripped = path.substring(storagePath.length() + offset);
        return stripped.split(File.separator);
    }

    /**
     * @return the path of the storage the file is stored on
     */
    private String getStoragePath() {
        String storagePath;
        if (isExternal) {
            storagePath = SAFAccessor.getExternalSDPath();
        } else {
            storagePath = Environment.getExternalStorageDirectory().getAbsolutePath();
        }
        return storagePath;
    }

    public DocumentFile createParentDocFile() throws SAFAccessor.SAFException {
        String[] parts = createRelativeFilePathParts(getStoragePath(), file);
        parts = Arrays.copyOf(parts, parts.length - 1);
        if (isExternal)
            return createExternalDoc(parts);
        else
            return createInternalDoc(parts);
    }

    public DocumentFile createDocFile() throws SAFAccessor.SAFException {
        String storagePath = getStoragePath();
        String[] parts;
        parts = createRelativeFilePathParts(storagePath, file);

        if (!isExternal) {
            return createInternalDoc(parts);
        }

        return createExternalDoc(parts);

    }

    private DocumentFile createInternalDoc(String[] parts) throws SAFAccessor.SAFException {
        if (internalCache == null)
            internalCache = new DocFileCache(SAFAccessor.getInternalRootDocFile(), 30);
        return internalCache.findDoc(parts);
    }



    private DocumentFile createExternalDoc(String[] parts) throws SAFAccessor.SAFException {
        if (externalCache == null)
            externalCache = new DocFileCache(SAFAccessor.getExternalRootDocFile(), 30);
        return externalCache.findDoc(parts);
    }

    @Override
    public boolean createNewFile() throws IOException {
        if (requiresSAF()) {
            try {
                DocumentFile folderDoc = createParentDocFile();
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

    public static class DocFileCreator {

        private static DocumentFile createExternalDoc(String[] parts) throws SAFAccessor.SAFException {
            DocumentFile doc = SAFAccessor.getExternalRootDocFile();
            for (String part : parts) {
                doc = doc.findFile(part);
            }
            return doc;
        }

//        private static String[] createRelativeFilePathParts(File file) {
//            String storagePath;
//            if (SAFAccessor.hasExternalSdCard() && file.getAbsolutePath().startsWith(SAFAccessor.getExternalSDPath())) {
//                storagePath = SAFAccessor.getExternalSDPath();
//            } else {
//                storagePath = Environment.getExternalStorageDirectory().getAbsolutePath();
//            }
//            String path = file.getAbsolutePath();
//            // if rootPath is null, there is no external sd card.
//            // if it isn't the share still might be on the internal "sd card"
//            // todo test, because it is untested cause I go to bed now
//            String stripped;
//            if (storagePath == null) {
//                Lok.error("STORAGE PATH WAS NULL");
//                throw new NullPointerException("storage path was null");
//            }
//            if (!path.startsWith(storagePath))
//                Lok.error("INVALID PATH! tried to find [" + path + "] in [" + storagePath + "]");
//            // +1 to get rid of the leading slash
//            stripped = path.substring(0,storagePath.length()+1);
//            return stripped.split(File.separator);
//        }
//
//        public static DocumentFile createParentDocFile(File file) throws SAFAccessor.SAFException {
//            String[] parts = createRelativeFilePathParts(file);
//            parts = Arrays.copyOf(parts, parts.length - 1);
//            return createExternalDoc(parts);
//        }
//
//        public static DocumentFile createDocFile(File file) throws SAFAccessor.SAFException {
//            String storagePath;
//            String[] parts;
//            final boolean external = SAFAccessor.hasExternalSdCard() && file.getAbsolutePath().startsWith(SAFAccessor.getExternalSDPath());
//            if (external) {
//                storagePath = SAFAccessor.getExternalSDPath();
//            } else {
//                storagePath = Environment.getExternalStorageDirectory().getAbsolutePath();
//            }
//            parts = createRelativeFilePathParts(file);
//            return createExternalDoc(parts);
//        }
    }

}
