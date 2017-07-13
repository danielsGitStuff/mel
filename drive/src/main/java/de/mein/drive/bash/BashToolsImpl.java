package de.mein.drive.bash;

import de.mein.auth.tools.N;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
}
