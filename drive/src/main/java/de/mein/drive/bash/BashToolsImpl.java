package de.mein.drive.bash;

import org.jdeferred.Promise;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Created by xor on 13.07.2017.
 */
public interface BashToolsImpl {

     void setBinPath(String binPath);

    Set<Long> getINodesOfDirectory(File file) throws IOException;

     Long getINodeOfFile(File file) throws IOException;

    /**
     * rm -rf
     *
     * @param directory
     */
    void rmRf(File directory) throws IOException;

    List<String> stuffModifiedAfter(File referenceFile, File directory, File pruneDir) throws IOException, BashToolsException;

     Stream<String> find(File directory, File pruneDir) throws IOException;

     Promise<Long, Exception, Void> getInode(File f);

    Stream<String> stuffModifiedAfter(File originalFile, File pruneDir, long timeStamp) throws IOException, InterruptedException;
}
