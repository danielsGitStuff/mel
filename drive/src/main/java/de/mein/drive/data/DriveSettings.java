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
    public static final String TRANSFER_DIR = "leTransfer";
    public static final float DEFAULT_WASTEBIN_RATIO = 0.1f;
    public static final int DEFAULT_WASTEBIN_MAXDAYS = 30;
    public static final int CACHE_LIST_SIZE = 2000;
    private RootDirectory rootDirectory;
    private String role = ":(";
    private Long lastSyncedVersion = 0l;
    private de.mein.drive.data.DriveClientSettingsDetails clientSettings;
    private de.mein.drive.data.DriveServerSettingsDetails serverSettings;
    private String transferDirectoryPath;
    private Long maxWastebinSize;
    private Long maxAge = 30L;

    public static RootDirectory buildRootDirectory(String path) throws IllegalAccessException, JsonSerializationException, JsonDeserializationException {
        RootDirectory rootDirectory = new RootDirectory().setPath(path);
        rootDirectory.setOriginalFile(AFile.instance(path));
        rootDirectory.backup();
        return rootDirectory;
    }

    public boolean isServer() {
        return role.equals(DriveStrings.ROLE_SERVER);
    }

    public DriveDetails getDriveDetails() {
        return new DriveDetails().setLastSyncVersion(lastSyncedVersion).setRole(role);
    }

    public String getTransferDirectoryPath() {
        return transferDirectoryPath;
    }

    public AFile getTransferDirectoryFile() {
        return AFile.instance(transferDirectoryPath);
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

    public interface DevRootDirInjector {
        File getRootDir(File jsonFile);
    }

    public static DevRootDirInjector devRootDirInjector;

    public DriveSettings() {

    }

    public de.mein.drive.data.DriveClientSettingsDetails getClientSettings() {
        return clientSettings;
    }

    public de.mein.drive.data.DriveServerSettingsDetails getServerSettings() {
        return serverSettings;
    }

    public DriveSettings setRole(String role) {
        this.role = role;
        if (role.equals(DriveStrings.ROLE_CLIENT) && clientSettings == null)
            clientSettings = new de.mein.drive.data.DriveClientSettingsDetails();
        else if (role.equals(DriveStrings.ROLE_SERVER) && serverSettings == null)
            serverSettings = new DriveServerSettingsDetails();
        return this;
    }

    public DriveSettings setRootDirectory(RootDirectory rootDirectory) {
        this.rootDirectory = rootDirectory;
        return this;
    }

    public DriveSettings setTransferDirectoryPath(String transferDirectoryPath) {
        this.transferDirectoryPath = transferDirectoryPath;
        return this;
    }

    /**
     * @param fsDao
     * @param jsonFile
     * @param driveSettingsCfg can hold values like RootDirectory if not already configured in the jsonFile
     * @return
     * @throws IOException
     * @throws JsonDeserializationException
     * @throws JsonSerializationException
     * @throws IllegalAccessException
     * @throws SqlQueriesException
     */
    public static DriveSettings load(FsDao fsDao, File jsonFile, DriveSettings driveSettingsCfg) throws IOException, JsonDeserializationException, JsonSerializationException, IllegalAccessException, SqlQueriesException {
        DriveSettings driveSettings = (DriveSettings) JsonSettings.load(jsonFile);
        if (driveSettings == null) {
            driveSettings = new DriveSettings();
            driveSettings.setJsonFile(jsonFile);
        }
        if (driveSettings.getRootDirectory() == null && driveSettingsCfg != null) {
            driveSettings.setRootDirectory(new RootDirectory().setPath(driveSettingsCfg.getRootDirectory().getPath()).backup());
        }
        driveSettings.getRootDirectory().backup();
        driveSettings.getRootDirectory().setOriginalFile(AFile.instance(driveSettings.getRootDirectory().getPath()));
        driveSettings.save();
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
