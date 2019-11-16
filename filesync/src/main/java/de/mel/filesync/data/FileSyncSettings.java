package de.mel.filesync.data;

import de.mel.auth.data.JsonSettings;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.filesync.data.fs.RootDirectory;
import de.mel.auth.file.AbstractFile;

import java.io.File;
import java.io.IOException;

/**
 * Created by xor on 30.08.2016.
 */
@SuppressWarnings("Duplicates")
public class FileSyncSettings extends JsonSettings {
    public static final float DEFAULT_WASTEBIN_RATIO = 0.1f;
    public static final int DEFAULT_WASTEBIN_MAXDAYS = 30;
    public static final int CACHE_LIST_SIZE = 2000;
    protected RootDirectory rootDirectory;
    protected String role = ":(";
    protected Long lastSyncedVersion = 0l;
    protected FileSyncClientSettingsDetails clientSettings;
    protected FileSyncServerSettingsDetails serverSettings;
    protected String transferDirectoryPath;
    protected Long maxWastebinSize;
    protected Long maxAge = 30L;
    protected AbstractFile transferDirectory;
    protected Boolean useSymLinks = true;

    protected boolean fastBoot = true;

    public static RootDirectory buildRootDirectory(AbstractFile rootFile) throws IllegalAccessException, JsonSerializationException, JsonDeserializationException, IOException {
        String path = rootFile.getCanonicalPath();
        RootDirectory rootDirectory = new RootDirectory().setPath(path);
        rootDirectory.setOriginalFile(rootFile);
        rootDirectory.backup();
        return rootDirectory;
    }

    public FileSyncSettings setUseSymLinks(Boolean useSymLinks) {
        this.useSymLinks = useSymLinks;
        return this;
    }

    public Boolean getUseSymLinks() {
        return useSymLinks;
    }

    public boolean isServer() {
        return role.equals(FileSyncStrings.ROLE_SERVER);
    }

    public FileSyncDetails getDriveDetails() {
        return new FileSyncDetails().setLastSyncVersion(lastSyncedVersion).setRole(role).setUsesSymLinks(useSymLinks);
    }


    public AbstractFile getTransferDirectoryFile() {
        return transferDirectory;
    }

    public Long getMaxWastebinSize() {
        return maxWastebinSize;
    }

    public FileSyncSettings setMaxWastebinSize(Long maxWastebinSize) {
        this.maxWastebinSize = maxWastebinSize;
        return this;
    }

    public Long getMaxAge() {
        return maxAge;
    }

    public FileSyncSettings setMaxAge(Long maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    @Override
    protected void init() {
        if (serverSettings != null)
            serverSettings.init();
    }

    public FileSyncSettings setFastBoot(boolean fastBoot) {
        this.fastBoot = fastBoot;
        return this;
    }

    public boolean getFastBoot() {
        return fastBoot;
    }

    public interface DevRootDirInjector {
        File getRootDir(File jsonFile);
    }

    public static DevRootDirInjector devRootDirInjector;

    public FileSyncSettings() {

    }

    public FileSyncClientSettingsDetails getClientSettings() {
        return clientSettings;
    }

    public FileSyncServerSettingsDetails getServerSettings() {
        return serverSettings;
    }

    public FileSyncSettings setRole(String role) {
        this.role = role;
        if (role.equals(FileSyncStrings.ROLE_CLIENT) && clientSettings == null)
            clientSettings = new FileSyncClientSettingsDetails();
        else if (role.equals(FileSyncStrings.ROLE_SERVER) && serverSettings == null)
            serverSettings = new FileSyncServerSettingsDetails();
        return this;
    }

    public FileSyncSettings setRootDirectory(RootDirectory rootDirectory) {
        this.rootDirectory = rootDirectory;
        return this;
    }

    public FileSyncSettings setTransferDirectory(AbstractFile transferDirectory) {
        this.transferDirectory = transferDirectory;
        this.transferDirectoryPath = transferDirectory.getAbsolutePath();
        return this;
    }

    public AbstractFile getTransferDirectory() {
        return transferDirectory;
    }

    public static FileSyncSettings load(File jsonFile) throws IOException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
        FileSyncSettings fileSyncSettings = (FileSyncSettings) JsonSettings.load(jsonFile);
        if (fileSyncSettings != null) {
            fileSyncSettings.setJsonFile(jsonFile);
            fileSyncSettings.getRootDirectory().backup();
            fileSyncSettings.setTransferDirectory(AbstractFile.instance(fileSyncSettings.transferDirectoryPath));
        }
        return fileSyncSettings;
    }

    public FileSyncSettings setLastSyncedVersion(Long lastSyncedVersion) {
        this.lastSyncedVersion = lastSyncedVersion;
        return this;
    }

    public Long getLastSyncedVersion() {
        return lastSyncedVersion;
    }

    public RootDirectory getRootDirectory() {
        return rootDirectory;
    }

    public String getRole() {
        return role;
    }
}
