package de.mein.auth.data.db;

import de.mein.core.serialize.SerializableEntity;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 2/26/16.
 */
public class Approval extends SQLTableObject implements SerializableEntity {
    private Pair<Long> id = new Pair<>(Long.class, "id");
    private Pair<Long> certificateId = new Pair<>(Long.class, "certificateid");
    private Pair<Long> serviceId = new Pair<>(Long.class, "serviceid");

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
        populateInsert(certificateId, serviceId);
        populateAll(id);
    }

    public Pair<Long> getCertificateId() {
        return certificateId;
    }

    public Approval setCertificateId(Long certificateId) {
        this.certificateId.v(certificateId);
        return this;
    }

    public Approval setServiceId(Long serviceId) {
        this.serviceId.v(serviceId);
        return this;
    }

    public Pair<Long> getServiceId() {
        return serviceId;
    }
}
