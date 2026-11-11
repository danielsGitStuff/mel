package de.mel.filesync.data;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by xor on 11/13/16.
 */
public class PathCollection {
    private List<File> paths = new ArrayList<>();
    private Set<String> pathSet = new HashSet<>();


    public List<File> getPaths() {
        return paths;
    }

    public PathCollection addPath(File file) {
        if (!pathSet.contains(file.getAbsolutePath())) {
            paths.add(file);
            pathSet.add(file.getAbsolutePath());
        }
        return this;
    }

    public void addAll(List<File> files) {
        for (File p : files) {
            addPath(p);
        }
    }
}
