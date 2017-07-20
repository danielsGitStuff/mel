package de.mein.drive.bash;

import org.jdeferred.Promise;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

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



    public static Set<Long> getINodesOfDirectory(File file) throws IOException {
        return instance.getINodesOfDirectory(file);
    }


    public static Long getINodeOfFile(File file) throws IOException {
        return instance.getINodeOfFile(file);
    }


    public static void rmRf(File directory) throws IOException {
        instance.rmRf(directory);
    }


    public static List<String> stuffModifiedAfter(File referenceFile, File directory, File pruneDir) throws IOException, BashToolsException {
        return instance.stuffModifiedAfter(referenceFile, directory, pruneDir);
    }


    public static Stream<String> find(File directory, File pruneDir) throws IOException {
        return instance.find(directory, pruneDir);
    }


    public static Promise<Long, Exception, Void> getInode(File f) {
        return instance.getInode(f);
    }

    public static Stream<String> stuffModifiedAfter(File originalFile, File pruneDir, long timeStamp) throws IOException, InterruptedException {
        return instance.stuffModifiedAfter(originalFile,pruneDir, timeStamp);
    }

    public static void setInstance(BashToolsImpl instance) {
        BashTools.instance = instance;
    }
}
