package de.mel.filesync.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.mel.auth.file.AbstractFile;

/**
 * Created by xor on 11/13/16.
 */
public class PathCollection {
    private List<AbstractFile<?>> paths = new ArrayList<>();
    private Set<String> pathSet = new HashSet<>();


    public List<AbstractFile<?>> getPaths() {
        return paths;
    }

    public PathCollection addPath(AbstractFile<?> file) {
        if (!pathSet.contains(file.getAbsolutePath())) {
            paths.add(file);
            pathSet.add(file.getAbsolutePath());
        }
        return this;
    }

    public void addAll(List<AbstractFile<?>> paths) {
        for (AbstractFile p : paths) {
            addPath(p);
        }
    }
}
