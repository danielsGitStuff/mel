package de.mein.auth.data.db;

import de.mein.core.serialize.SerializableEntity;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 2/26/16.
 */
public class Approval extends SQLTableObject implements SerializableEntity {
    private Pair<Long> id = new Pair<>(Long.class, "id");
    private Pair<Long> certificateId = new Pair<>(Long.class, "certificateId");
    private Pair<Long> serviceid = new Pair<>(Long.class, "serviceid");

    public Approval() {
        init();
    }

    public Pair<Long> getId() {
        return id;
    }

    @Override
    public String getTableName() {
        return "approval";
    }

    @Override
    protected void init() {
        populateInsert(certificateId, serviceid);
        populateAll(id);
    }

    public Pair<Long> getCertificateId() {
        return certificateId;
    }

    public Approval setCertificateId(Long certificateId) {
        this.certificateId.v(certificateId);
        return this;
    }

    public Approval setServiceid(Long serviceid) {
        this.serviceid.v(serviceid);
        return this;
    }

    public Pair<Long> getServiceid() {
        return serviceid;
    }
}
