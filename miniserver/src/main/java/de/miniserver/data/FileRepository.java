package de.miniserver.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

public class FileRepository {
    private Map<String, File> hashFileMap = new HashMap<>();

    public FileRepository addEntry(String hash, File file) {
        hashFileMap.put(hash, file);
        return this;
    }

    public File getFile(String hash) throws Exception {
        if (hashFileMap.containsKey(hash))
            return hashFileMap.get(hash);
        throw new FileNotFoundException("no file for " + (hash == null ? "null" : hash));
    }
}
