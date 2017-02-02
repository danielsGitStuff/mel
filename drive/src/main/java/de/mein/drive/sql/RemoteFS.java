package de.mein.drive.sql;

import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 04.09.2016.
 */
public class RemoteFS extends SQLTableObject {
    private Pair<Long> fsEntryId = new Pair<>(Long.class, "fsentryid");
    private Pair<Long> remoteId = new Pair<>(Long.class, "remoteid");

    public RemoteFS() {
        init();
    }

    @Override
    public String getTableName() {
        return "remote";
    }

    @Override
    protected void init() {
        populateInsert(fsEntryId, remoteId);
        populateAll();
    }


    public Pair<Long> getFsEntryId() {
        return fsEntryId;
    }

    public Pair<Long> getRemoteId() {
        return remoteId;
    }
}
