package de.mein.android.service;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;

import de.mein.Lok;
import de.mein.android.Tools;
import de.mein.android.file.JFile;
import de.mein.android.file.SAFAccessor;
import de.mein.auth.tools.N;
import de.mein.auth.tools.NWrap;
import de.mein.sql.Hash;

public class CopyService extends IntentService {
    public static final String TRGT_PATH = "target";
    public static final String SRC_PATH = "src";
    public static final String MOVE = "mv";
    public static final int BUFFER_SIZE = 1024 * 64;

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     */
    public CopyService() {
        super(CopyService.class.getSimpleName());
    }


    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        final boolean move = intent.getBooleanExtra(MOVE, false);
        final String srcPath = intent.getStringExtra(SRC_PATH);
        final String targetPath = intent.getStringExtra(TRGT_PATH);
        JFile src = new JFile(srcPath);
        JFile target = new JFile(targetPath);
        String msg = (move ? "moving" : "copying") + " '" + srcPath + "' -> '" + targetPath + "'";
        Log.d(getClass().getSimpleName(), msg);
        NWrap<InputStream> fis = new NWrap<>(null);
        NWrap<OutputStream> fos = new NWrap<>(null);
        try {
            DocumentFile srcDoc = src.createDocFile();
            DocumentFile targetDoc = target.createDocFile();
            if (targetDoc == null) {
                DocumentFile targetParentDoc = target.createParentDocFile();
                if (targetParentDoc == null)
                    throw new FileNotFoundException("directory does not exist: " + targetPath);
                JFile jtarget = new JFile(target);
                jtarget.createNewFile();
                targetDoc = target.createDocFile();
            }
            ContentResolver resolver = Tools.getApplicationContext().getContentResolver();
            fis.v = resolver.openInputStream(srcDoc.getUri());
            fos.v = resolver.openOutputStream(targetDoc.getUri());

            copyStream(fis.v, fos.v);
            if (move) {
                srcDoc.delete();
            }
        } catch (SAFAccessor.SAFException | IOException e) {
            e.printStackTrace();
        } finally {
            N.s(() -> fis.v.close());
            N.s(() -> fos.v.close());
        }
        Lok.debug("CopyService.onHandleIntent");
    }

    public void copyStream(InputStream in, OutputStream out) throws IOException {
        int read = 0;
        do {
            byte[] bytes = new byte[BUFFER_SIZE];
            read = in.read(bytes);
            if (read > 0) {
                out.write(bytes, 0, read);
            }
        } while (read > 0);
    }
}
