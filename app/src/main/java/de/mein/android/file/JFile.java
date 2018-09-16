package de.mein.android.file;

import android.content.ContentResolver;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.support.v4.content.MimeTypeFilter;
import android.support.v4.provider.DocumentFile;
import android.webkit.MimeTypeMap;

import com.archos.filecorelibrary.ExtStorageManager;
import com.archos.filecorelibrary.FileComparator;
import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.MimeUtils;
import com.archos.filecorelibrary.localstorage.JavaFile2;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import de.mein.android.Tools;
import de.mein.android.drive.data.NC;
import de.mein.auth.file.AFile;
import de.mein.auth.tools.N;
import de.mein.auth.tools.NWrap;

public class JFile extends AFile<JFile> {

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
    public boolean move(JFile target) {
        return getFileEditor().move(target.file.getUri());
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
        try {
            getFileEditor().delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public JFile getParentFile() {
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
        return null;
    }

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
        Uri parentUri = parentFile.file.getUri();
        String path = parentUri.getPath();
        String internalPath = Environment.getDataDirectory().getAbsolutePath();
        if (path.startsWith(internalPath)) {
            File f = new File(parentUri.getPath() + File.separator + file.getName());
            return f.createNewFile();
        }
//        String auth = parentFile.file.getUri().getAuthority();
//        if (auth.length() == 0){
//            File f = new File(parentUri.getPath()+File.separator+file.getName());
//            return f.createNewFile();
//        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ContentResolver resolver = Tools.getApplicationContext().getContentResolver();
            UriPermission permission = resolver.getPersistedUriPermissions().get(0);
            parentUri = permission.getUri();
            String rootDocId = DocumentsContract.getTreeDocumentId(parentUri);
            String storage = ExtStorageManager.getExtStorageManager().getExtSdcards().get(0);
            String stripped = path.substring(storage.length() + 1);
            String[] parts = stripped.split(File.separator);
            NWrap<Uri> parentWrap = new NWrap<>(parentUri);
            NWrap.SWrap idWrap = new NWrap.SWrap(null);
            N.forEach(parts, (stoppable, index, part) -> {
                String docId = DocumentsContract.getTreeDocumentId(parentWrap.v());
                Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentWrap.v(), docId);
                Cursor cursor = resolver.query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
                NC.iterate(cursor, (cursor1, stoppable1) -> {
                    String name = cursor1.getString(1);
                    if (name.equals(part)) {
                        String id = cursor.getString(0);
                        Uri uri = DocumentsContract.buildDocumentUriUsingTree(parentWrap.v(), id);
                        //uri = DocumentsContract.buildTreeDocumentUri(uri.getAuthority(), id);
                        idWrap.v(id);
                        parentWrap.v(uri);
                        stoppable1.stop();
                    }
                });
            });
            parentUri = parentWrap.v();
//            DocumentFile doc = DocumentFile.fromSingleUri(Tools.getApplicationContext(), parentUri);
//            String docId = DocumentsContract.getDocumentId(doc.getUri());
//            parentUri = DocumentsContract.buildDocumentUri(doc.getUri().getAuthority(), docId);
            //parentUri = doc.getUri();
            String mime = "text/plain";
            mime = "application/octet-stream";
            // tree uri -> document uri
            // parentUri = DocumentsContract.buildDocumentUri(parentUri.getAuthority(), idWrap.v());
            Uri docUri = DocumentsContract.createDocument(Tools.getApplicationContext().getContentResolver(), parentUri, mime, file.getName());
            if (docUri != null) {
                file = new JavaFile2(new File(docUri.getPath()));
                return true;
            }
            return false;
        }
        return false;
    }

    private JFile[] list(FileFilter fileFilter) {
        final ArrayList<JavaFile2> content = new ArrayList<JavaFile2>();


        File directory = new File(file.getUri().getPath());

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

        for (File f : listFiles) {
            if (f.isDirectory()) {
                content.add(new JavaFile2(f, JavaFile2.NUMBER_UNKNOWN, JavaFile2.NUMBER_UNKNOWN));
            } else if (f.isFile()) {
                content.add(new JavaFile2(f));
            }
        }

        Collections.sort(content, (o1, o2) -> o1.getName().compareTo(o2.getName()));

        JFile[] result = N.arr.fromCollection(content, N.converter(JFile.class, element -> new JFile(element)));
        return result;
    }

    @Override
    public JFile[] listContent() {
        return list(null);
    }

    public Uri getUri() {
        return file.getUri();
    }
}
