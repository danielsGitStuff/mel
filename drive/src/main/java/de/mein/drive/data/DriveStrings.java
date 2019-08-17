package de.mein.drive.data;

import org.jetbrains.annotations.Nullable;

/**
 * Created by xor on 10/26/16.
 */
public abstract class DriveStrings {
    public static final int DB_VERSION = 1;

    // communication related stuff
    public static final String NAME = "MeinDrive";
    public static final String INTENT_DRIVE_DETAILS = "getDriveDetails"; //
    public static final String INTENT_REG_AS_CLIENT = "regAsClient"; //
    public static final String ROLE_SERVER = "server";
    public static final String ROLE_CLIENT = "client";
    public static final String INTENT_SYNC = "syncFromServer";
    public static final String INTENT_DIRECTORY_CONTENT = "dircontent";
    public static final String INTENT_PLEASE_TRANSFER = "transfer";
    public static final String INTENT_PROPAGATE_NEW_VERSION = "nVersion";
    public static final String INTENT_COMMIT = "commit";
    public static final String INTENT_HASH_AVAILABLE = "hash.avail";
    public static final String INTENT_ASK_HASHES_AVAILABLE = "hash.please";

    // file system related stuff
    public static final String WASTEBIN = "wastebin";
    public static final String DB_FILENAME = "meindrive.db";
    public static final String SETTINGS_FILE_NAME = "drive.settings.json";
    public static final String STAGESET_SOURCE_STARTUP_INDEX = "startup";
    public static final String STAGESET_SOURCE_FS = "fs";
    public static final String STAGESET_SOURCE_MERGED = "merged";
    public static final String STAGESET_SOURCE_SERVER = "server";
    public static final String STAGESET_SOURCE_CLIENT = "client";
    public static final String STAGESET_STATUS_STAGING = "staging";
    public static final String STAGESET_STATUS_STAGED = "staged";
    public static final String STAGESET_STATUS_SERVER_COMMITED = "server.com.done";
    public static final String STAGESET_STATUS_DELETE = "del";
    public static final String TRANSFER_DIR = "ttransfer";

    public class Notifications {
        public static final String INTENTION_CONFLICT_DETECTED = "drive.conflict.detected";
        public static final String INTENTION_PROGRESS = "drive.progress";
        public static final String INTENTION_BOOT = "drive.spawn";
        public static final String INTENTION_OUT_OF_SPACE = "drive.oos";
    }
}
