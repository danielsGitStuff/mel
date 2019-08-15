package de.mein.drive.bash;

import de.mein.drive.sql.FsEntry;
import org.jdeferred.Promise;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.mein.auth.file.AFile;

/**
 * Created by xor on 13.07.2017.
 */
public interface BashToolsImpl {

    void setBinPath(String binPath);

    Set<Long> getINodesOfDirectory(AFile file) throws IOException;

    FsBashDetails getFsBashDetails(AFile file) throws IOException, InterruptedException;

    /**
     * rm -rf
     *
     * @param directory
     */
    void rmRf(AFile directory) throws IOException;

    List<AFile> stuffModifiedAfter(AFile referenceFile, AFile directory, AFile pruneDir) throws IOException, BashToolsException;

    AutoKlausIterator<AFile<?>> find(AFile directory, AFile pruneDir) throws IOException;

    AutoKlausIterator<AFile> stuffModifiedAfter(AFile originalFile, AFile pruneDir, long timeStamp) throws IOException, InterruptedException;

    void mkdir(AFile dir) throws IOException;

    boolean mv(File source, File target) throws IOException;

    boolean isSymLink(AFile f);

    Map<String, FsBashDetails> getContentFsBashDetails(AFile file);

    void lnS(AFile file, String target);
}
