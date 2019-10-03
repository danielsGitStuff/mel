package de.mel.filesync.data;

import de.mel.auth.data.ServicePayload;

/**
 * Created by xor on 10/20/16.
 */
public class FileSyncDetails extends ServicePayload {
    private String role;
    private long lastSyncVersion;
    private String serviceUuid;
    private boolean usesSymLinks = false;
    private Long directoryCount;

    public FileSyncDetails setDirectoryCount(Long directoryCount) {
        this.directoryCount = directoryCount;
        return this;
    }

    public Long getDirectoryCount() {
        return directoryCount;
    }

    public FileSyncDetails setLastSyncVersion(long lastSyncVersion) {
        this.lastSyncVersion = lastSyncVersion;
        return this;
    }

    public FileSyncDetails setRole(String role) {
        this.role = role;
        return this;
    }

    public FileSyncDetails setServiceUuid(String serviceUuid) {
        this.serviceUuid = serviceUuid;
        return this;
    }

    public String getServiceUuid() {
        return serviceUuid;
    }

    public long getLastSyncVersion() {
        return lastSyncVersion;
    }

    public String getRole() {
        return role;
    }

    public FileSyncDetails setUsesSymLinks(boolean usesSymLinks) {
        this.usesSymLinks = usesSymLinks;
        return this;
    }

    public boolean usesSymLinks() {
        return usesSymLinks;
    }
}
