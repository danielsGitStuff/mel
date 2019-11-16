package de.mel.filesync.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;

/**
 * Created by xor on 11/13/16.
 */
public class PathCollection {
    private List<IFile> paths = new ArrayList<>();
    private Set<String> pathSet = new HashSet<>();


    public List<IFile> getPaths() {
        return paths;
    }

    public PathCollection addPath(IFile file) {
        if (!pathSet.contains(file.getAbsolutePath())) {
            paths.add(file);
            pathSet.add(file.getAbsolutePath());
        }
        return this;
    }

    public void addAll(List<IFile> files) {
        for (IFile p : files) {
            addPath(p);
        }
    }
}
