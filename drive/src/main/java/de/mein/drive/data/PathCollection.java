package de.mein.drive.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by xor on 11/13/16.
 */
public class PathCollection {
    private List<String> paths = new ArrayList<>();
    private Set<String> pathSet = new HashSet<>();

    public List<String> getPaths() {
        return paths;
    }

    public PathCollection addPath(String path) {
        if (!pathSet.contains(path)) {
            System.out.println("PathCollection.addPath: "+path);
            paths.add(path);
            pathSet.add(path);
        }
        return this;
    }

    public void addAll(List<String> paths) {
        for (String p : paths) {
            addPath(p);
        }
    }
}
