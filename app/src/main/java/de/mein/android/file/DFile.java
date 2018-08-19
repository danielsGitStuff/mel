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

    public DFile(String path) {
        Context context = getContext();
        try {
            uri = Uri.parse(path);
            boolean isDocUri = DocumentFile.isDocumentUri(context, uri);
            if (isDocUri) {
                rawFile = false;
            }else {
                rawFile = true;
            }
        } catch (Exception e) {
            this.uri = DocumentFile.fromFile(new File(path)).getUri();
            rawFile = true;
        }
        System.out.println("DFile.DFile");
    }

    public DFile(File file) {
        this.uri = DocumentFile.fromFile(file).getUri();
        rawFile = true;
    }

    public DFile(DocumentFile documentFile) {
        this.documentFile = documentFile;
        rawFile = false;
        uri = documentFile.getUri();
    }

    public DFile(DFile parent, DocumentFile documentFile) {
        this(documentFile);
        this.parent = parent;
    }

    @Override
    protected AFile constructSubFile(String name) {
        if (rawFile) {
            return new DFile(uri.getEncodedPath() + getSeparator() + name);
        } else {
            String str = uri.toString();
            Object lastPath = documentFile.getUri().getLastPathSegment();
            Uri append;
            if (str.endsWith("%3A"))
                append = Uri.parse(documentFile.getUri().toString() + name);
            else
                append = Uri.parse(documentFile.getUri().toString() + AFile.separator() + name);
            DocumentFile sub = DocumentFile.fromSingleUri(getContext(), append);
//            try {
//                Uri u = DocumentsContract.createDocument(getContext().getContentResolver(), documentFile.getUri(), null, name);
//                return new DFile(DocumentFile.fromSingleUri(getContext(), u));
//            } catch (FileNotFoundException e) {
//                e.printStackTrace();
//            }
//            DocumentFile sub2 = DocumentFile.fromSingleUri(getContext(),append);
//            String a = sub.getUri().getEncodedPath();
            return new DFile(this, sub);
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
        spawnDoc();
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
//            if (documentFile.isDirectory() && documentFile.exists())
//                return false;
            Stack<DFile> stack = new Stack<>();
            DFile toCreate = this;
            if (toCreate != null && !toCreate.exists()) {
                stack.push(toCreate);
                toCreate = toCreate.parent;
            }
            //the last parent has to exist
            DFile root = toCreate;
            while (!stack.empty()) {
                DFile dir = stack.pop();
                try {
                    Uri created = DocumentsContract.createDocument(getContext().getContentResolver(), root.uri, null, dir.getName());
                    dir.setUri(created);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
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

    @Override
    public Long getUsableSpace() {
        try {
            ParcelFileDescriptor descriptor = getContext().getContentResolver().openFileDescriptor(documentFile.getUri(), "r");
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
        return documentFile.lastModified();
    }

    @Override
    public boolean createNewFile() throws IOException {
        if (rawFile){
         return new File(uri.getEncodedPath()).createNewFile();
        }else {
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
