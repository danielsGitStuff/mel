package de.mein.drive.tasks;

import de.mein.auth.data.cached.data.CachedIterable;

import java.io.File;

public class AvailableHashesContainer extends CachedIterable<AvailHashEntry> {

    public AvailableHashesContainer(File cacheDir, int partSize) {
        super(cacheDir,partSize);
    }

    public AvailableHashesContainer() {
    }
}
