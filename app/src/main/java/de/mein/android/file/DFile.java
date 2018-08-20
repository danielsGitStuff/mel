package de.mein.android.file;


import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.support.v4.provider.DocumentFile;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStatVfs;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;

import de.mein.auth.file.AFile;

/**
 * this maps {@link File} methods to androids storage access framework
 */
public class DFile extends AFile {


    // this is only created if needed!
    private DocumentFile documentFile;
    // this only exists if it already exists on the file system
    private Uri uri;
    // this is used for the stuff in the private folder only
    private boolean rawFile;
    // this is required to create the uri if necessary
    private String name;
    // this is required except this is the root element
    private DFile parent;


    /**
     * use this if this instance should behave like a root directory. eg. all operations you want to do in the future happen inside this directory.
     *
     * @param documentFile
     */
    public DFile(DocumentFile documentFile) {
        this.name = documentFile.getName();
        this.uri = documentFile.getUri();
        this.rawFile = false;
    }

    /**
     * deprecation:
     * cannot ensure we get the right {@link DocumentFile}.
     * It delivers the root element only.
     * obviously we cannot extract the name from there.
     *
     * @param path
     */
    @Deprecated
    public DFile(String path) {
        uri = Uri.parse(path);
        if (uri.getAuthority() == null) {
            DocumentFile documentFile = DocumentFile.fromFile(new File(path));
            this.name = documentFile.getName();
            this.uri = Uri.parse(uri.getEncodedPath());
            rawFile = true;
        } else {
            System.err.println("DFile.DFile(path): you fed a path that seems to be a URI. URIs are unsafe on android.");
            System.err.println("DFile.DFile(path): the received DocumentFiles probably do not match the URIs");
            System.err.println("DFile.DFile(path): please use DFile.DFile(parent,name) instead!");
            System.err.println("DFile.DFile(path): the fed path was: " + path);
            rawFile = false;
            if (DocumentsContract.isTreeUri(uri)) {
                documentFile = DocumentFile.fromTreeUri(getContext(), uri);
            } else if (DocumentsContract.isDocumentUri(getContext(), uri)) {
                documentFile = DocumentFile.fromSingleUri(getContext(), uri);
            }
            this.name = documentFile.getName();
        }
    }


    public DFile(DFile parent, String name) {
        this.name = name;
        this.parent = parent;
        this.rawFile = parent.rawFile;
        if (this.rawFile) {
            this.uri = Uri.parse(parent.uri + File.separator + name);
        }
        readDocumentUri();
    }

    public DFile(File file) {
        DocumentFile documentFile = DocumentFile.fromFile(file);
        uri = Uri.parse(documentFile.getUri().getEncodedPath());
        name = file.getName();
        rawFile = true;
    }

    private void readDocumentUri() {
        if (parent != null && parent.uri != null && !rawFile) {
            boolean parentTree = DocumentsContract.isTreeUri(parent.uri);
            Uri childrenUri;//= DocumentsContract.buildChildDocumentsUriUsingTree(parent.uri, DocumentsContract.getTreeDocumentId(parent.uri));
            try {
                //for childs and sub child dirs
                childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent.uri, DocumentsContract.getDocumentId(parent.uri));
            } catch (Exception e) {
                // for parent dir
                childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent.uri, DocumentsContract.getTreeDocumentId(parent.uri));
            }
            Cursor c = getContext().getContentResolver().query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}
                    , DocumentsContract.Document.COLUMN_DISPLAY_NAME + " = ?", new String[]{name}, null);
            try {
                uri = null;
                while (c.moveToNext()) {
                    final String docId = c.getString(0);
                    final String name = c.getString(1);
                    final String mime = c.getString(2);
                    uri = DocumentsContract.buildDocumentUriUsingTree(childrenUri, docId);
                }
            } finally {
                c.close();
            }
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getSeparator() {
        return rawFile ? File.separator : "%2F";
    }

    @Override
    public String getAbsolutePath() {
        if (rawFile)
            return uri.getEncodedPath();
        return uri.toString();
    }

    private void spawnDoc() {
        if (DocumentsContract.isTreeUri(uri)) {
//            documentFile = DocumentFile.fromTreeUri(getContext(), uri);
        } else if (DocumentsContract.isDocumentUri(getContext(), uri)) {
//            documentFile = DocumentFile.fromSingleUri(getContext(), uri);
        } else
            documentFile = DocumentFile.fromFile(new File(uri.toString()));
    }

    @Override
    public boolean exists() {
        if (rawFile) {
            return new File(uri.getEncodedPath()).exists();
        } else if (uri != null) {
            DocumentFile documentFile = DocumentFile.fromTreeUri(getContext(), uri);
            return documentFile.exists();
        }
        return false;
    }

    @Override
    public boolean isFile() {
        if (rawFile) {
            return new File(uri.getEncodedPath()).isFile();
        }
        spawnDoc();
        return documentFile.isFile();
    }

    @Override
    public boolean renameTo(AFile target) {
        spawnDoc();
        return documentFile.renameTo("KKKKKK");
    }

    @Override
    public boolean isDirectory() {
        if (rawFile) {
            return new File(uri.getEncodedPath()).isDirectory();
        }
        spawnDoc();
        return documentFile.isDirectory();
    }

    @Override
    public Long length() {
        if (rawFile)
            return new File(uri.getEncodedPath()).length();
        spawnDoc();
        try {
            return parcelFileDescriptor().getStatSize();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return -2L;
        }
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
        return false;
    }

    @Override
    public AFile getParentFile() {
        return null;
    }

    @Override
    public boolean mkdirs() {
        if (rawFile) {
            return new File(uri.getEncodedPath()).mkdirs();
        } else {
            if (documentFile != null && documentFile.exists())
                return false;
            if (parent != null) {
                if (!parent.exists())
                    parent.mkdirs();
            }
            if (uri == null)
                readDocumentUri();
            if (uri != null) {
                DocumentFile documentFile = DocumentFile.fromTreeUri(getContext(), uri);
                if (documentFile.exists())
                    return false;
            }
//            DocumentFile parentDoc;
//            if (DocumentsContract.isTreeUri(parent.uri)) {
//                parentDoc = DocumentFile.fromTreeUri(getContext(), parent.uri);
//            } else {
//                parentDoc = DocumentFile.fromSingleUri(getContext(), parent.uri);
//            }
//            DocumentFile ins = parentDoc.createDirectory(name);
//            uri = ins.getUri();
            return true;
        }
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

    private ParcelFileDescriptor parcelFileDescriptor() throws FileNotFoundException {
        return getContext().getContentResolver().openFileDescriptor(uri, "r");
    }

    @Override
    public Long getUsableSpace() {
        if (rawFile) {
            return new File(uri.getEncodedPath()).getUsableSpace();
        }
        try {
            spawnDoc();
            ParcelFileDescriptor descriptor = parcelFileDescriptor();
            StructStatVfs stats = Os.fstatvfs(descriptor.getFileDescriptor());
            return stats.f_bfree;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (ErrnoException e) {
            e.printStackTrace();
        }
        return -1L;
    }

    private Context getContext() {
        AndroidFileConfiguration configuration = getAndroidConfiguration();
        return configuration.getContext();
    }

    private AndroidFileConfiguration getAndroidConfiguration() {
        return (AndroidFileConfiguration) AFile.getConfiguration();
    }

    @Override
    public Long lastModified() {
        if (rawFile)
            return new File(uri.getEncodedPath()).lastModified();
        spawnDoc();
        return documentFile.lastModified();
    }

    @Override
    public boolean createNewFile() throws IOException {
        if (rawFile) {
            return new File(uri.getEncodedPath()).createNewFile();
        } else {
            spawnDoc();
            if (!documentFile.exists()) {
                if (rawFile) {
                    return new File(uri.getEncodedPath()).createNewFile();
                } else {
                    documentFile.getParentFile().createFile(null, documentFile.getName());
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public AFile[] listContent() {
        return new AFile[0];
    }

    public void setUri(Uri uri) {
        this.uri = uri;
        rawFile = false;
        documentFile = DocumentsContract.isTreeUri(uri) ? DocumentFile.fromTreeUri(getContext(), uri) : DocumentFile.fromSingleUri(getContext(), uri);
    }
}
