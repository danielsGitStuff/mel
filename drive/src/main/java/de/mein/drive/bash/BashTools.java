package de.mein.drive.bash;

import org.jdeferred.Promise;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import de.mein.auth.file.AFile;

/**
 * Created by xor on 10/28/16.
 */
@SuppressWarnings("Duplicates")
public abstract class BashTools {


    private static BashToolsImpl instance;

    public static void init() {
        if (instance == null)
            if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
                instance = new BashToolsWindows();
            } else {
                instance = new BashToolsUnix();
            }
    }

    public static void setBinPath(String binPath) {
        instance.setBinPath(binPath);
    }


    public static Set<Long> getINodesOfDirectory(AFile file) throws IOException {
        return instance.getINodesOfDirectory(file);
    }


    public static ModifiedAndInode getINodeOfFile(AFile file) throws IOException, InterruptedException {
        return instance.getModifiedAndINodeOfFile(file);
    }


    public static void rmRf(AFile directory) throws IOException {
        instance.rmRf(directory);
    }


    public static List<String> stuffModifiedAfter(AFile referenceFile, AFile directory, AFile pruneDir) throws IOException, BashToolsException {
        return instance.stuffModifiedAfter(referenceFile, directory, pruneDir);
    }


    public static Iterator<String> find(AFile directory, AFile pruneDir) throws IOException {
        return instance.find(directory, pruneDir);
    }


    public static Promise<Long, Exception, Void> getInode(AFile f) {
        return instance.getInode(f);
    }

    public static Iterator<String> stuffModifiedAfter(AFile originalFile, AFile pruneDir, long timeStamp) throws IOException, InterruptedException {
        return instance.stuffModifiedAfter(originalFile, pruneDir, timeStamp);
    }

    public static void setInstance(BashToolsImpl instance) {
        BashTools.instance = instance;
    }

    public static Iterator<String> inputStreamToIterator(InputStream inputStream) {
        BufferedIterator bufferedReader = new BufferedIterator(new InputStreamReader(inputStream));
        return bufferedReader.iterator();
    }

    public static void mkdir(AFile dir) throws IOException {
        int i = 0;
        while (!dir.exists()) {
            dir.mkdirs();
            System.out.println("BashTools.mkdir(" + i + ") for " + dir.getAbsolutePath());
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
}
