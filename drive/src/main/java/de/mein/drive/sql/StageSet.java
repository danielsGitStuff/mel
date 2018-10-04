package de.mein.drive.sql;

import de.mein.drive.data.DriveStrings;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 12/8/16.
 */
public class StageSet extends SQLTableObject {
    private static final String ID = "id";
    private static final String SOURCE = "source";
    private static final String ORIGIN_CERT = "origincert";
    private static final String ORIGIN_SERVICE = "originservice";
    private static final String STATUS = "status";
    private static final String CREATED = "created";
    private static final String VERSION = "version";
    private Pair<Long> id = new Pair<>(Long.class, ID);
    private Pair<String> source = new Pair<>(String.class, SOURCE);
    private Pair<Long> originCertId = new Pair<>(Long.class, ORIGIN_CERT);
    private Pair<String> originServiceUuid = new Pair<>(String.class, ORIGIN_SERVICE);
    private Pair<String> status = new Pair<>(String.class, STATUS);
    private Pair<Long> created = new Pair<>(Long.class, CREATED);
    private Pair<Long> version = new Pair<>(Long.class, VERSION);

    public StageSet() {
        init();
    }


    @Override
    public String getTableName() {
        return "stageset";
    }

    @Override
    protected void init() {
        populateInsert(source, originCertId, originServiceUuid, status, version);
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

    public Pair<String> getSource() {
        return source;
    }

    public StageSet setId(Long id) {
        this.id.v(id);
        return this;
    }

    public StageSet setSource(String source) {
        this.source.v(source);
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

    public StageSet setVersion(Long version) {
        this.version.v(version);
        return this;
    }

    public Pair<Long> getVersion() {
        return version;
    }

    public Pair<Long> getCreated() {
        return created;
    }

    public Pair<String> getStatus() {
        return status;
    }

    public boolean fromFs() {
        return source.v().equals(DriveStrings.STAGESET_SOURCE_FS);
    }

    @Override
    public String toString() {
        return "id: " + id.v() + " src: " + source.v() + " status: " + status.v();
    }
}
