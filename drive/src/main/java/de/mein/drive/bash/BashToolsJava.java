package de.mein.drive.bash;

import org.jdeferred.Promise;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Created by xor on 7/24/17.
 */

public class BashToolsJava implements BashToolsImpl {
    @Override
    public void setBinPath(String binPath) {

    }

    @Override
    public Set<Long> getINodesOfDirectory(File file) throws IOException {
        return null;
    }

    @Override
    public Long getINodeOfFile(File file) throws IOException {
        return null;
    }

    @Override
    public void rmRf(File directory) throws IOException {

    }

    @Override
    public List<String> stuffModifiedAfter(File referenceFile, File directory, File pruneDir) throws IOException, BashToolsException {
        return null;
    }

    @Override
    public Iterator<String> find(File directory, File pruneDir) throws IOException {
        return null;
    }

    @Override
    public Promise<Long, Exception, Void> getInode(File f) {
        return null;
    }

    @Override
    public Iterator<String> stuffModifiedAfter(File originalFile, File pruneDir, long timeStamp) throws IOException, InterruptedException {
        return null;
    }
}
