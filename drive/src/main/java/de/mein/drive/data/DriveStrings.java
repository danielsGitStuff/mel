package de.mein.drive.data;

/**
 * Created by xor on 10/26/16.
 */
public abstract class DriveStrings {
    public static final int DB_VERSION = 1;

    // communication related stuff
    public static final String NAME = "MeinDrive";
    public static final String INTENT_DRIVE_DETAILS = "getDriveDetails";
    public static final String INTENT_REG_AS_CLIENT = "regAsClient";
    public static final String ROLE_SERVER = "server";
    public static final String ROLE_CLIENT = "client";
    public static final String INTENT_SYNC = "syncThisClient";
    public static final String INTENT_DIRECTORY_CONTENT = "dircontent";
    public static final String INTENT_PLEASE_TRANSFER = "transfer";
    public static final String INTENT_PROPAGATE_NEW_VERSION = "nVersion";
    public static final String INTENT_COMMIT = "commit";

    // file system related stuff
    public static final String WASTEBIN = "wastebin";
    public static final String DB_FILENAME = "meindrive.db";
    public static final String STAGESET_TYPE_STARTUP_INDEX = "startup";
    public static final String STAGESET_TYPE_FS = "fs";
    public static final String STAGESET_TYPE_MERGED = "merged";
    public static final String STAGESET_TYPE_STAGING_FROM_SERVER = "staging from server";
    public static final String STAGESET_TYPE_FROM_CLIENT = "from client";
    public static final String STAGESET_STATUS_STAGING = "staging";
    public static final String STAGESET_STATUS_STAGED = "staged";
    public static final String STAGESET_STATUS_SERVER_COMMITED = "server.com.done";

    public class Notifications {
        public static final String CONFLICT_DETECTED = "drive.conflict.detected";
    }
}
