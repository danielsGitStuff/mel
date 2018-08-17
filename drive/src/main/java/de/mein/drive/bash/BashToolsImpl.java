package de.mein.drive.bash;

import org.jdeferred.Promise;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import de.mein.auth.file.AFile;

/**
 * Created by xor on 13.07.2017.
 */
public interface BashToolsImpl {

     void setBinPath(String binPath);

    Set<Long> getINodesOfDirectory(AFile file) throws IOException;

     ModifiedAndInode getModifiedAndINodeOfFile(AFile file) throws IOException, InterruptedException;

    /**
     * rm -rf
     *
     * @param directory
     */
    void rmRf(AFile directory) throws IOException;

    List<String> stuffModifiedAfter(AFile referenceFile, AFile directory, AFile pruneDir) throws IOException, BashToolsException;

    Iterator<String> find(AFile directory, AFile pruneDir) throws IOException;

     Promise<Long, Exception, Void> getInode(AFile f);

    Iterator<String> stuffModifiedAfter(AFile originalFile, AFile pruneDir, long timeStamp) throws IOException, InterruptedException;

    void mkdir(AFile dir) throws IOException;
}
