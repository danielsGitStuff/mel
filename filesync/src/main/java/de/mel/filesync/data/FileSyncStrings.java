package de.mel.filesync.data;

/**
 * Created by xor on 10/26/16.
 */
public abstract class FileSyncStrings {
    public static final int DB_VERSION = 1;

    // communication related stuff
    public static final String NAME = "File Sync";
    public static final String INTENT_DRIVE_DETAILS = "getFileSyncDetails"; //
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
    public static final String DB_FILENAME = "melfilesync.db";
    public static final String SETTINGS_FILE_NAME = "filesync.settings.json";
    public static final String STAGESET_SOURCE_STARTUP_INDEX = "startup";
    public static final String STAGESET_SOURCE_FS = "fs";
    public static final String STAGESET_SOURCE_MERGED = "merged";
    public static final String STAGESET_SOURCE_SERVER = "server";
    public static final String STAGESET_SOURCE_CLIENT = "client";
    public static final String STAGESET_STATUS_STAGING = "staging";
    public static final String STAGESET_STATUS_STAGED = "staged";
    public static final String STAGESET_STATUS_SERVER_COMMITED = "server.com.done";
    public static final String STAGESET_STATUS_DELETE = "del";
    public static final String TRANSFER_DIR = ".mel.filesync.transfer";

    public class Notifications {
        public static final String INTENTION_CONFLICT_DETECTED = "filesync.conflict.detected";
        public static final String INTENTION_PROGRESS = "filesync.progress";
        public static final String INTENTION_BOOT = "filesync.spawn";
        public static final String INTENTION_OUT_OF_SPACE = "filesync.oos";
    }
}
