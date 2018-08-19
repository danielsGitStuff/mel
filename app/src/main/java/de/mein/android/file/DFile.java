package de.mein.android.file;


import android.content.ContentProvider;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.support.v4.provider.DocumentFile;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStatVfs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Stack;

import de.mein.auth.file.AFile;
import de.mein.auth.tools.N;

/**
 * this maps {@link File} methods to androids storage access framework
 */
public class DFile extends AFile {


    private DFile parent;
    // this is only created if needed!
    private DocumentFile documentFile;
    private Uri uri;
    private boolean rawFile;
    private String name;

    public DFile(String path) {
        Context context = getContext();
//        try {
        uri = Uri.parse(path);
        if (uri.getAuthority() == null) {
            DocumentFile documentFile = DocumentFile.fromFile(new File(path));
            this.name = documentFile.getName();
            rawFile = true;
        } else {
            rawFile = false;
            spawnDoc();
            this.name = documentFile.getName();
        }
//        } catch (Exception e) {
//            DocumentFile documentFile = DocumentFile.fromFile(new File(path));
//            this.uri = documentFile.getUri();
//            this.name = documentFile.getName();
//            rawFile = true;
//        }
    }

    public DFile(File file) {
        this.uri = DocumentFile.fromFile(file).getUri();
        rawFile = true;
    }

    public DFile(DocumentFile documentFile) {
        this.documentFile = documentFile;
        rawFile = false;
        if (documentFile.getName() != null)
            name = documentFile.getName();
        uri = documentFile.getUri();
    }

    public DFile(DFile parent, DocumentFile documentFile) {
        this(documentFile);
        this.parent = parent;
    }

    public DFile(DFile parent, String name) {
        this.uri = Uri.parse(parent.getAbsolutePath()+parent.getSeparator()+name);
        this.name = name;
        this.parent = parent;
    }

    @Override
    protected AFile constructSubFile(String name) {
        if (rawFile) {
            DFile dFile = new DFile(uri.getEncodedPath() + getSeparator() + name);
            dFile.parent = this;
            return dFile;
        } else {
            String str = uri.toString();
            Uri append;
            if (str.endsWith("%3A"))
                append = Uri.parse(uri.toString() + name);
            else
                append = Uri.parse(uri.toString() + AFile.separator() + name);
            DocumentFile sub = DocumentFile.fromSingleUri(getContext(), append);
//            try {
//                Uri u = DocumentsContract.createDocument(getContext().getContentResolver(), documentFile.getUri(), null, name);
//                return new DFile(DocumentFile.fromSingleUri(getContext(), u));
//            } catch (FileNotFoundException e) {
//                e.printStackTrace();
//            }
//            DocumentFile sub2 = DocumentFile.fromSingleUri(getContext(),append);
//            String a = sub.getUri().getEncodedPath();
            return new DFile(this, name);
        }
    }

    @Override
    public String getName() {
        return null;
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
        if (DocumentsContract.isTreeUri(uri))
            documentFile = DocumentFile.fromTreeUri(getContext(), uri);
        else if (DocumentsContract.isDocumentUri(getContext(), uri))
            documentFile = DocumentFile.fromSingleUri(getContext(), uri);
        else
            documentFile = DocumentFile.fromFile(new File(uri.toString()));
    }

    @Override
    public boolean exists() {
        spawnDoc();
        return documentFile.exists();
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
            spawnDoc();
            if (documentFile.exists())
                return false;
            if (parent != null) {
                if (!parent.exists())
                    parent.mkdirs();
            }
            try {
                Uri created = DocumentsContract.createDocument(getContext().getContentResolver(), parent.uri, null, name);
                this.uri = created;
                return true;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
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
