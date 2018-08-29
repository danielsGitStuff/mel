package de.mein.android.file;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import de.mein.auth.file.AFile;

public class DFileRecursiveIterator implements Iterator<AFile> {

    private final AFile pruneDir;
    private Cursor cursor;
    private final Context context;
    private final DFile currentDir;
    private Queue<DFile> currentSubDirs = new LinkedList<>();
    private DFileRecursiveIterator subIterator;

    public DFileRecursiveIterator(Context context, DFile rootDirectory, AFile pruneDir) {
        this.context = context;
        this.pruneDir = pruneDir;
        this.currentDir = rootDirectory;
        Uri childrenUri = rootDirectory.buildChildrenUri();
        this.cursor = context.getContentResolver().query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null);

    }

    @Override
    public boolean hasNext() {
        boolean next = !cursor.isAfterLast() || (subIterator != null && subIterator.hasNext());
        if (!next)
            cursor.close();
        return next;
    }

    @Override
    public AFile next() {
        if (hasNext()) {
            if (cursor.isAfterLast()) {
                cursor.close();
                if (subIterator != null)
                    return subIterator.next();
                while (!currentSubDirs.isEmpty()) {
                    DFile subDir = currentSubDirs.poll();
                    subIterator = new DFileRecursiveIterator(context, currentDir, null);
                    if (subIterator.hasNext())
                        return subIterator.next();

                }
            } else {
                cursor.moveToNext();
                String name = cursor.getString(0);
                String mime = cursor.getString(1);
                String id = cursor.getString(2);
                DFile dFile = new DFile(currentDir, name);
                if (dFile.isDirectory()) {
                    currentSubDirs.add(dFile);
                }
                return dFile;
            }
        }
        return null;
    }
}
