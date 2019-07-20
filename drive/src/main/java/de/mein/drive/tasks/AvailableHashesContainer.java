package de.mein.drive.tasks;

import de.mein.auth.data.cached.CachedList;
import de.mein.auth.service.Bootloader;

import java.io.File;

public class AvailableHashesContainer extends CachedList<AvailHashEntry> {

    public AvailableHashesContainer(File cacheDir, Long cacheId, int partSize) {
        super(cacheDir, cacheId, partSize);
        this.level = Bootloader.BootLevel.LONG;
    }

    public AvailableHashesContainer() {
        this.level = Bootloader.BootLevel.LONG;
    }
}
