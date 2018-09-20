package de.mein.auth.data.db;

import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 6/25/16.
 */
public class ServiceJoinServiceType extends SQLTableObject implements SerializableEntity {
    @JsonIgnore
    private Pair<Long> serviceId;
    @JsonIgnore
    private Pair<Boolean> active;
    private Pair<String> uuid, type, description, name;
    @JsonIgnore
    private boolean running;


    public ServiceJoinServiceType() {
        init();
    }

    @Override
    public String getTableName() {
        return null;
    }

    @Override
    protected void init() {
        Service service = new Service();
        ServiceType serviceType = new ServiceType();
        serviceId = service.getId();
        uuid = service.getUuid();
        type = serviceType.getType();
        name = service.getName();
        active = service.getActivePair();
        description = serviceType.getDescription();
        populateInsert();
        populateAll(serviceId, uuid, type, description, name, active);
    }

    public Pair<String> getType() {
        return type;
    }

    public Pair<String> getUuid() {
        return uuid;
    }

    public Pair<String> getDescription() {
        return description;
    }

    public Pair<Long> getServiceId() {
        return serviceId;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public boolean isRunning() {
        return running;
    }

    public Pair<String> getName() {
        return name;
    }

    public Pair<Boolean> getActive() {
        return active;
    }
}
