package de.mein.drive.sql;

import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 12/16/16.
 */
public class TransferDetails extends SQLTableObject {

    private static final String ID = "id";
    private static final String HASH = "hash";
    private static final String CERTIFICATE_ID = "certid";
    private static final String SERVICE_UUID = "serviceuuid";
    private static final String SIZE = "size";
    private static final String STARTED = "started";

    private Pair<Long> id = new Pair<>(Long.class, ID);
    private Pair<String> hash = new Pair<>(String.class, HASH);
    private Pair<Long> certId = new Pair<>(Long.class, CERTIFICATE_ID);
    private Pair<String> serviceUuid = new Pair<>(String.class, SERVICE_UUID);
    private Pair<Long> size = new Pair<>(Long.class, SIZE);
    private Pair<Boolean> started = new Pair<>(Boolean.class, STARTED, false);

    public TransferDetails() {
        init();
    }

    @Override
    public String getTableName() {
        return "transfer";
    }

    @Override
    protected void init() {
        populateInsert(hash, certId, serviceUuid, size, started);
        populateAll(id);
    }

    public Pair<Long> getCertId() {
        return certId;
    }

    public Pair<Long> getId() {
        return id;
    }

    public Pair<String> getHash() {
        return hash;
    }

    public Pair<Long> getSize() {
        return size;
    }

    public Pair<String> getServiceUuid() {
        return serviceUuid;
    }

    public Pair<Boolean> getStarted() {
        return started;
    }
}
