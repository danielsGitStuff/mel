package de.mein.android.drive.bash;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import org.jdeferred.Promise;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import de.mein.android.drive.data.NC;
import de.mein.android.file.AndroidFileConfiguration;
import de.mein.android.file.DFile;
import de.mein.android.file.DFileRecursiveIterator;
import de.mein.auth.file.AFile;
import de.mein.drive.bash.BashToolsException;
import de.mein.drive.bash.BashToolsImpl;
import de.mein.drive.bash.ModifiedAndInode;

public class SAFBashTools implements BashToolsImpl {
    private final Context context;
    private AndroidFileConfiguration configuration;
    private BashToolsAndroid bashToolsAndroid;

    public SAFBashTools(Context context) {
        this.context = context;
        AFile.Configuration configuration = AFile.getConfiguration();
        if (configuration == null) {
            System.err.println("SAFBashTools.SAFBashTools: " + AFile.class.getSimpleName() + " was not configured. Call " + AFile.class.getSimpleName() + ".configure() before calling this.");
        } else if (!(configuration instanceof AndroidFileConfiguration)) {
            System.err.println("SAFBashTools.SAFBashTools: " + AFile.class.getSimpleName() + " was improperly configured. Configuration type is " + configuration.getClass().getSimpleName() + " but " + AndroidFileConfiguration.class.getSimpleName() + " was expected. Call " + AFile.class.getSimpleName() + ".configure() before calling this.");
        } else {
            this.configuration = (AndroidFileConfiguration) AFile.getConfiguration();
            bashToolsAndroid = new BashToolsAndroid(context);
        }
    }


    @Override
    public void setBinPath(String binPath) {
        //nothing to do here
    }

    @Override
    public Set<Long> getINodesOfDirectory(AFile file) throws IOException {
//        DFile dFile = (DFile) file;
//        if (dFile.isRawFile())
//            return bashToolsAndroid.getINodesOfDirectory(dFile);
//
//        configuration.getContext().getContentResolver().query(dFile.getUri(), DocumentsContract.Document.COLUMN_DISPLAY_NAME,)
        return null;
    }

    @Override
    public ModifiedAndInode getModifiedAndINodeOfFile(AFile file) throws IOException, InterruptedException {
        return null;
    }

    @Override
    public void rmRf(AFile directory) throws IOException {

    }

    @Override
    public List<AFile> stuffModifiedAfter(AFile referenceFile, AFile directory, AFile pruneDir) throws IOException, BashToolsException {
        return null;
    }

    @Override
    public Iterator<AFile> find(AFile directory, AFile pruneDir) throws IOException {
        DFile dDirectory = (DFile) directory;
        if (dDirectory.isRawFile())
            return bashToolsAndroid.find(dDirectory, pruneDir);
//
//        Uri childrenUri = dDirectory.buildChildrenUri();
//        Cursor cursor = context.getContentResolver().query(childrenUri, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null);
//        NC.iterate(cursor, (cursor1, stoppable) -> {
//            String name = cursor.getString(0);
//            String id = cursor.getString(1);
//            System.out.println("SAFBashTools.find.name: " + name+", id: "+id);
//        });
        return new DFileRecursiveIterator(context,dDirectory,pruneDir);
    }

    @Override
    public Promise<Long, Exception, Void> getInode(AFile f) {
        return null;
    }

    @Override
    public Iterator<AFile> stuffModifiedAfter(AFile originalFile, AFile pruneDir, long timeStamp) throws IOException, InterruptedException {
        return null;
    }

    @Override
    public void mkdir(AFile dir) throws IOException {

    }
}
