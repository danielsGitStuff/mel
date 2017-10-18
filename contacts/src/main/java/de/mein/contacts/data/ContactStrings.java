package de.mein.contacts.data;

/**
 * Created by xor on 9/23/17.
 */

public class ContactStrings {
    public static final String DB_FILENAME = "contacts.db";
    public static final int DB_VERSION = 1;
    public static final String ROLE_SERVER = "server";
    public static final String ROLE_CLIENT = "client";

    public static final String INTENT_QUERY = "query";
    public static final String INTENT_UPDATE = "update";
    public static final String NAME = "Contact Sync";

    public class Notifications {

        public static final String INTENTION_CONFLICT = "conflict";
        public static final String INTENT_EXTRA_CONFLICT = "cnflct";
    }
}
