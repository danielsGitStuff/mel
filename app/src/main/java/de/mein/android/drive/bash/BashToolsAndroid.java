package de.mein.android.drive.bash;

import org.jdeferred.Promise;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import de.mein.drive.bash.BashTools;
import de.mein.drive.bash.BashToolsException;
import de.mein.drive.bash.BashToolsImpl;
import de.mein.drive.bash.BashToolsJava;
import de.mein.drive.bash.BashToolsUnix;

/**
 * Created by xor on 7/20/17.
 */

public class BashToolsAndroid extends BashToolsUnix {
    private BashToolsJava javaFallback;

    public BashToolsAndroid(){
        javaFallback = new BashToolsJava();
    }
    @Override
    public Stream<String> find(File directory, File pruneDir) throws IOException {
        return super.find(directory, pruneDir);
    }
}
