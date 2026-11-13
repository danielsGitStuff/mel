package de.mel.filesync.service.sync;

import de.mel.auth.data.MelServiceProperties;
import de.mel.filesync.service.MelFileSyncService;

public class FileSyncServiceProperties extends MelServiceProperties<MelFileSyncService<?>> {
    protected long spaceTotalBytes, spaceFreeBytes;

    public FileSyncServiceProperties(MelFileSyncService<?> service, long spaceTotalBytes, long spaceFreeBytes) {
        super(service);
        this.spaceTotalBytes = spaceTotalBytes;
        this.spaceFreeBytes = spaceFreeBytes;
    }

    public long getSpaceFreeBytes() {
        return spaceFreeBytes;
    }

    public long getSpaceTotalBytes() {
        return spaceTotalBytes;
    }
}
