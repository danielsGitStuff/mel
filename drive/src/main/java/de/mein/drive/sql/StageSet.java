package de.mein.drive.sql;

import de.mein.drive.data.DriveStrings;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 12/8/16.
 */
public class StageSet extends SQLTableObject {
    private static final String ID = "id";
    private static final String TYPE = "type";
    private static final String ORIGIN_CERT = "origincert";
    private static final String ORIGIN_SERVICE = "originservice";
    private static final String STATUS = "status";
    private static final String CREATED = "created";
    private Pair<Long> id = new Pair<>(Long.class, ID);
    private Pair<String> type = new Pair<>(String.class, TYPE);
    private Pair<Long> originCertId = new Pair<>(Long.class, ORIGIN_CERT);
    private Pair<String> originServiceUuid = new Pair<>(String.class, ORIGIN_SERVICE);
    private Pair<String> status = new Pair<>(String.class, STATUS);
    private Pair<Long> created = new Pair<>(Long.class, CREATED);

    public StageSet() {
        init();
    }


    @Override
    public String getTableName() {
        return "stageset";
    }

    @Override
    protected void init() {
        populateInsert(type, originCertId, originServiceUuid, status);
        populateAll(id, created);
    }

    public Pair<Long> getId() {
        return id;
    }

    public Pair<Long> getOriginCertId() {
        return originCertId;
    }

    public Pair<String> getOriginServiceUuid() {
        return originServiceUuid;
    }

    public Pair<String> getType() {
        return type;
    }

    public StageSet setId(Long id) {
        this.id.v(id);
        return this;
    }

    public StageSet setType(String type) {
        this.type.v(type);
        return this;
    }

    public StageSet setOriginCertId(Long originCertId) {
        this.originCertId.v(originCertId);
        return this;
    }

    public StageSet setOriginServiceUuid(String originServiceUuid) {
        this.originServiceUuid.v(originServiceUuid);
        return this;
    }

    public StageSet setStatus(String status) {
        this.status.v(status);
        return this;
    }

    public Long getCreated() {
        return created.v();
    }

    public Pair<String> getStatus() {
        return status;
    }

    public boolean fromFs() {
        return type.v().equals(DriveStrings.STAGESET_TYPE_FS);
    }
}
