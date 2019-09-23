package de.mein.drive.bash;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.mein.Lok;
import de.mein.auth.file.AFile;

/**
 * Created by xor on 10/28/16.
 */
@SuppressWarnings("Duplicates")
public abstract class BashTools {

    public static final boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
    private static BashToolsImpl instance;

    public static BashToolsImpl getInstance() {
        return instance;
    }

    public static void setInstance(BashToolsImpl instance) {
        BashTools.instance = instance;
    }

    public static void init() {
        if (instance == null)
            if (isWindows) {
                instance = new BashToolsWindows();
            } else {
                instance = new BashToolsUnix();
            }
    }

    public static void setBinPath(String binPath) {
        instance.setBinPath(binPath);
    }

    public static FsBashDetails getFsBashDetails(AFile file) throws IOException, InterruptedException {
        return instance.getFsBashDetails(file);
    }

    public static void rmRf(AFile directory) throws IOException {
        instance.rmRf(directory);
    }

    public static void rmRf(File directory) throws IOException {
        instance.rmRf(AFile.instance(directory));
    }

    public static List<AFile<?>> stuffModifiedAfter(AFile referenceFile, AFile directory, AFile pruneDir) throws IOException, BashToolsException {
        return instance.stuffModifiedAfter(referenceFile, directory, pruneDir);
    }

    public static AutoKlausIterator<AFile<?>> find(AFile directory, AFile pruneDir) throws IOException {
        return instance.find(directory, pruneDir);
    }

    public static AutoKlausIterator<AFile<?>> stuffModifiedAfter(AFile originalFile, AFile pruneDir, long timeStamp) throws IOException, InterruptedException {
        return instance.stuffModifiedAfter(originalFile, pruneDir, timeStamp);
    }

    public static AutoKlausIterator<AFile> inputStreamToFileIterator(InputStream inputStream) {
        BufferedIterator bufferedReader = new BufferedIterator.BufferedFileIterator(new InputStreamReader(inputStream));
        return bufferedReader;
    }

    public static AutoKlausIterator<String> inputStreamToIterator(InputStream inputStream) {
        BufferedIterator bufferedReader = new BufferedIterator.BufferedStringIterator(new InputStreamReader(inputStream));
        return bufferedReader;
    }

    public static void mkdir(AFile dir) throws IOException {
        int i = 0;
        while (!dir.exists()) {
            dir.mkdirs();
            Lok.debug("BashTools.mkdir(" + i + ") for " + dir.getAbsolutePath());
        }
        //instance.mkdir(dir);
    }

    public static File[] lsD(String path) {
        File root = new File(path);
        if (root.exists()) {
            File[] dirs = root.listFiles((file, s) -> file.isDirectory());
            return dirs;
        }
        return new File[0];
    }


    public static boolean isSymLink(AFile f) {
        return instance.isSymLink(f);
    }

    public static Map<String, FsBashDetails> getContentFsBashDetails(AFile file) {
        return instance.getContentFsBashDetails(file);
    }

    public static void lnS(AFile file, String target) {
        Lok.debug("creating symlink: '" + file.getAbsolutePath() + "' -> '" + target + "'");
        instance.lnS(file, target);
    }

    public static void setCreationDate(AFile target, Long created) {
        if (created != null)
            instance.setCreationDate(target, created);
    }
}
