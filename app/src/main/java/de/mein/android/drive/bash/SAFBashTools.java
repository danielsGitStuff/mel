package de.mein.android.drive.bash;

import android.content.Context;

import org.jdeferred.Promise;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.mein.android.file.AndroidFileConfiguration;
import de.mein.auth.file.AFile;
import de.mein.drive.bash.BashToolsException;
import de.mein.drive.bash.BashToolsImpl;
import de.mein.drive.bash.FsBashDetails;

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
    public FsBashDetails getFsBashDetails(AFile file) throws IOException, InterruptedException {
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
    public Iterator<AFile<?>> find(AFile directory, AFile pruneDir) throws IOException {
        //testing needed which one is faster.
        return bashToolsAndroid.find(directory, pruneDir);
//        return new DFileRecursiveIterator(context, directory, pruneDir);
    }

    @Override
    public Iterator<AFile> stuffModifiedAfter(AFile originalFile, AFile pruneDir, long timeStamp) throws IOException, InterruptedException {
        return null;
    }

    @Override
    public void mkdir(AFile dir) throws IOException {

    }

    @Override
    public boolean mv(File source, File target) throws IOException {
        bashToolsAndroid.mv(source, target);
        return false;
    }

    @Override
    public boolean isSymLink(AFile f) {
        return false;
    }

    @Override
    public Map<String, FsBashDetails> getContentFsBashDetails(AFile file) {
        return bashToolsAndroid.getContentFsBashDetails(file);
    }

    @Override
    public void lnS(AFile file, String target) {
        // symlinks do not work on Android
    }
}
