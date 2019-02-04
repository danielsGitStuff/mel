package de.mein.drive.data;

import de.mein.auth.data.JsonSettings;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.drive.data.fs.RootDirectory;
import de.mein.auth.file.AFile;
import de.mein.drive.sql.dao.FsDao;
import de.mein.sql.SqlQueriesException;

import java.io.File;
import java.io.IOException;

/**
 * Created by xor on 30.08.2016.
 */
@SuppressWarnings("Duplicates")
public class DriveSettings extends JsonSettings {
    public static final float DEFAULT_WASTEBIN_RATIO = 0.1f;
    public static final int DEFAULT_WASTEBIN_MAXDAYS = 30;
    public static final int CACHE_LIST_SIZE = 2000;
    private RootDirectory rootDirectory;
    private String role = ":(";
    private Long lastSyncedVersion = 0l;
    private DriveClientSettingsDetails clientSettings;
    private DriveServerSettingsDetails serverSettings;
    private String transferDirectoryPath;
    private Long maxWastebinSize;
    private Long maxAge = 30L;
    private AFile transferDirectory;
    private Boolean initFinished = false;
    private boolean fastBoot = true;

    public static RootDirectory buildRootDirectory(AFile rootFile) throws IllegalAccessException, JsonSerializationException, JsonDeserializationException {
        RootDirectory rootDirectory = new RootDirectory().setPath(rootFile.getAbsolutePath());
        rootDirectory.setOriginalFile(rootFile);
        rootDirectory.backup();
        return rootDirectory;
    }

    public boolean isServer() {
        return role.equals(DriveStrings.ROLE_SERVER);
    }

    public DriveDetails getDriveDetails() {
        return new DriveDetails().setLastSyncVersion(lastSyncedVersion).setRole(role);
    }


    public AFile getTransferDirectoryFile() {
        return transferDirectory;
    }

    public Long getMaxWastebinSize() {
        return maxWastebinSize;
    }

    public DriveSettings setMaxWastebinSize(Long maxWastebinSize) {
        this.maxWastebinSize = maxWastebinSize;
        return this;
    }

    public Long getMaxAge() {
        return maxAge;
    }

    public DriveSettings setMaxAge(Long maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    @Override
    protected void init() {

    }

    public DriveSettings setFastBoot(boolean fastBoot) {
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

    public DriveSettings() {

    }

    public DriveSettings setInitFinished(Boolean initFinished) {
        this.initFinished = initFinished;
        return this;
    }

    public Boolean getInitFinished() {
        return initFinished;
    }

    public DriveClientSettingsDetails getClientSettings() {
        return clientSettings;
    }

    public DriveServerSettingsDetails getServerSettings() {
        return serverSettings;
    }

    public DriveSettings setRole(String role) {
        this.role = role;
        if (role.equals(DriveStrings.ROLE_CLIENT) && clientSettings == null)
            clientSettings = new DriveClientSettingsDetails();
        else if (role.equals(DriveStrings.ROLE_SERVER) && serverSettings == null)
            serverSettings = new DriveServerSettingsDetails();
        return this;
    }

    public DriveSettings setRootDirectory(RootDirectory rootDirectory) {
        this.rootDirectory = rootDirectory;
        return this;
    }

    public DriveSettings setTransferDirectory(AFile transferDirectory) {
        this.transferDirectory = transferDirectory;
        this.transferDirectoryPath = transferDirectory.getAbsolutePath();
        return this;
    }

    public AFile getTransferDirectory() {
        return transferDirectory;
    }

    public static DriveSettings load(File jsonFile) throws IOException, JsonDeserializationException, JsonSerializationException, IllegalAccessException {
        DriveSettings driveSettings = (DriveSettings) JsonSettings.load(jsonFile);
        if (driveSettings != null) {
            driveSettings.setJsonFile(jsonFile);
            driveSettings.getRootDirectory().backup();
            driveSettings.setTransferDirectory(AFile.instance(driveSettings.transferDirectoryPath));
        }
        return driveSettings;
    }

    public DriveSettings setLastSyncedVersion(Long lastSyncedVersion) {
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
