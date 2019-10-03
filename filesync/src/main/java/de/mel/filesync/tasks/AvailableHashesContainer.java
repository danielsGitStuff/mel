package de.mel.filesync.tasks;

import de.mel.auth.data.cached.CachedList;
import de.mel.auth.service.Bootloader;

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
