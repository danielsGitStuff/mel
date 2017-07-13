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
public class BashToolsWindows implements BashToolsImpl {
    @Override
    public void setBinPath(String binPath) {
        System.out.println("BashToolsWindows.setBinPath");
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
        System.out.println("BashToolsWindows.rmRf");
    }

    @Override
    public List<String> stuffModifiedAfter(File referenceFile, File directory, File pruneDir) throws IOException, BashToolsException {
        return null;
    }

    @Override
    public Stream<String> find(File directory, File pruneDir) throws IOException {
        return null;
    }

    @Override
    public Promise<Long, Exception, Void> getInode(File f) {
        return null;
    }
}
