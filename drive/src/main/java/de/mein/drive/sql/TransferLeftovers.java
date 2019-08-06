package de.mein.drive.sql;

import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

public class TransferLeftovers extends SQLTableObject {
    private Pair<Long> bytesTransferred = new Pair<>(Long.class, "bytesdone");
    private Pair<Long> filesTransferred = new Pair<>(Long.class, "filesdone");
    private Pair<Long> bytesTotal = new Pair<>(Long.class, "bytestotal");
    private Pair<Long> filesTotal = new Pair<>(Long.class, "filestotal");

    @Override
    public String getTableName() {
        return new DbTransferDetails().getTableName();
    }

    public Pair<Long> getBytesTransferred() {
        return bytesTransferred;
    }

    public Pair<Long> getFilesTransferred() {
        return filesTransferred;
    }

    public Pair<Long> getBytesTotal() {
        return bytesTotal;
    }

    public Pair<Long> getFilesTotal() {
        return filesTotal;
    }

    @Override
    protected void init() {
        populateInsert(bytesTransferred, filesTransferred, bytesTotal, filesTotal);
        populateAll();
    }
}
