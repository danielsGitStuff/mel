package de.mein.drive.tasks;

import de.mein.Lok;
import de.mein.auth.data.cached.CachedIterable;

import java.io.File;

public class AvailableHashesContainer extends CachedIterable<AvailHashEntry> {

    public AvailableHashesContainer(File cacheDir, int partSize) {
        super(cacheDir,partSize);
    }

    public AvailableHashesContainer() {
    }
}
