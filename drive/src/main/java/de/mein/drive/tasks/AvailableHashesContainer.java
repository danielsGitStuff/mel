package de.mein.drive.tasks;

import de.mein.Lok;
import de.mein.auth.data.cached.CachedIterable;
import de.mein.auth.service.Bootloader;

import java.io.File;

public class AvailableHashesContainer extends CachedIterable<AvailHashEntry> {

    public AvailableHashesContainer(File cacheDir, int partSize) {
        super(cacheDir, partSize);
        this.level = Bootloader.BootLevel.LONG;
    }

    public AvailableHashesContainer() {
        this.level = Bootloader.BootLevel.LONG;
    }
}
