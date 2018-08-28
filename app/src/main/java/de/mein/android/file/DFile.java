package de.mein.android.file;


import android.content.Context;
import android.database.Cursor;
import android.media.MediaDescription;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.support.v4.app.BundleCompat;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStatVfs;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;

import de.mein.android.drive.data.NC;
import de.mein.auth.file.AFile;
import de.mein.auth.tools.N;
import de.mein.drive.data.DriveStrings;

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

    public DFile(DFile originalFile) {
        if (originalFile == null) {
            System.out.println("DFile.DFile");
        }
        this.name = originalFile.name;
        this.uri = originalFile.uri;
        if (originalFile.parent != null)
            this.parent = new DFile(originalFile.parent);
        this.rawFile = originalFile.rawFile;
        this.documentFile = originalFile.documentFile;
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
        if (path.equals("content://com.android.externalstorage.documents/tree/1A16-1611%3Athisisexternal/document/1A16-1611%3Athisisexternal%2Fttransfer"))
            System.out.println("DFile.DFile.debug");
        uri = Uri.parse(path);
        if (uri.getAuthority() == null) {
            DocumentFile documentFile = DocumentFile.fromFile(new File(path));
            this.name = documentFile.getName();
            this.uri = Uri.parse(uri.getEncodedPath());
            String mime = documentFile.getType();
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
        //todo debug
        if (name.equals(DriveStrings.TRANSFER_DIR))
            System.out.println("DFile.DFile");
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

    public Uri getUri() {
        return uri;
    }

    public void setUri(Uri uri) {
        this.uri = uri;
        rawFile = false;
        documentFile = DocumentsContract.isTreeUri(uri) ? DocumentFile.fromTreeUri(getContext(), uri) : DocumentFile.fromSingleUri(getContext(), uri);
    }

    public boolean isRawFile() {
        return rawFile;
    }

    public DFile getParent() {
        return parent;
    }

    @Override
    public String toString() {
        if (uri != null)
            return uri.toString();
        else if (parent != null && name != null)
            return parent.toString() + parent.getSeparator() + name;
        return "none";
    }

    public Uri buildChildrenUri() {
        return DFile.buildChildrenUri(this.uri);
    }

    public static Uri buildChildrenUri(Uri uri) {
        Uri childrenUri;//= DocumentsContract.buildChildDocumentsUriUsingTree(parent.uri, DocumentsContract.getTreeDocumentId(parent.uri));
        try {
            //for childs and sub child dirs
            childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getDocumentId(uri));
        } catch (Exception e) {
            // for parent dir
            childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
        }
        return childrenUri;
    }

    private void readDocumentUri() {
        if (parent != null && parent.uri != null && !rawFile) {
            boolean parentTree = DocumentsContract.isTreeUri(parent.uri);
            Uri childrenUri = buildChildrenUri(parent.uri);
            // SAF completely ignores the selection >:(
            // bonus: it ignores the selection applied to the URI too
            Cursor c = getContext().getContentResolver().query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}
                    , null, null, null);
            NC.iterate(c, (cursor, stoppable) -> {
                final String docId = c.getString(0);
                final String name = c.getString(1);
                final String mime = c.getString(2);
                if (name.equals(this.name)) {
                    uri = DocumentsContract.buildDocumentUriUsingTree(childrenUri, docId);
                    stoppable.stop();
                }
            });
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
    public boolean move(AFile target) {
        spawnDoc();
        return documentFile.renameTo(target.getName());
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
        Uri childrenUri = buildChildrenUri(uri);
        Cursor cursor = getContext().getContentResolver().query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, DocumentsContract.Document.COLUMN_MIME_TYPE + " != ?", new String[]{DocumentsContract.Document.MIME_TYPE_DIR}, null);
        AFile[] result = new AFile[cursor.getCount()];
        int index = 0;
        while (cursor.moveToNext()) {
            String name = cursor.getString(0);
            DFile file = new DFile(this, name);
            result[index] = file;
            index++;
        }
        return result;
    }

    @Override
    public AFile[] listDirectories() {
        Uri childrenUri = buildChildrenUri(uri);
        Cursor cursor = getContext().getContentResolver().query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, DocumentsContract.Document.COLUMN_MIME_TYPE + " = ?", new String[]{DocumentsContract.Document.MIME_TYPE_DIR}, null);
        AFile[] result = new AFile[cursor.getCount()];
        int index = 0;
        while (cursor.moveToNext()) {
            String name = cursor.getString(0);
            DFile dir = new DFile(this, name);
            result[index] = dir;
            index++;
        }
        return result;
    }

    @Override
    public boolean delete() {
        try {
            return DocumentsContract.deleteDocument(getContext().getContentResolver(), uri);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public AFile getParentFile() {
        return parent;
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
                String mime = documentFile.getType();
                if (documentFile.exists())
                    return false;
            }
            try {
                uri = DocumentsContract.createDocument(getContext().getContentResolver(), parent.uri, DocumentsContract.Document.MIME_TYPE_DIR, name);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
//            DocumentFile parentDoc;
//            if (DocumentsContract.isTreeUri(parent.uri)) {
//                parentDoc = DocumentFile.fromTreeUri(getContext(), parent.uri);
//            } else {
//                parentDoc = DocumentFile.fromSingleUri(getContext(), parent.uri);
//            }
//            String mime2 = parentDoc.getType();
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
        Uri childrenUri = buildChildrenUri(uri);
        Cursor cursor = getContext().getContentResolver().query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
        AFile[] result = new AFile[cursor.getCount()];
        int index = 0;
        while (cursor.moveToNext()) {
            String name = cursor.getString(0);
            DFile elem = new DFile(this, name);
            result[index] = elem;
            index++;
        }
        return result;
    }
}
