package de.mein.drive.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.mein.Lok;
import de.mein.auth.file.AFile;

/**
 * Created by xor on 11/13/16.
 */
public class PathCollection {
    private List<AFile<?>> paths = new ArrayList<>();
    private Set<String> pathSet = new HashSet<>();


    public List<AFile<?>> getPaths() {
        return paths;
    }

    public PathCollection addPath(AFile file) {
        if (!pathSet.contains(file.getAbsolutePath())) {
            Lok.debug("PathCollection.addPath: "+file);
            paths.add(file);
            pathSet.add(file.getAbsolutePath());
        }
        return this;
    }

    public void addAll(List<AFile> paths) {
        for (AFile p : paths) {
            addPath(p);
        }
    }
}
