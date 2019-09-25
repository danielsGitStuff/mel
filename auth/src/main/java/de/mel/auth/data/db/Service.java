package de.mel.auth.data.db;

import de.mel.auth.tools.N;
import de.mel.core.serialize.JsonIgnore;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.sql.Pair;
import de.mel.sql.SQLTableObject;

/**
 * Created by xor on 2/29/16.
 */
public class Service extends SQLTableObject implements SerializableEntity {
    private Pair<Long> id = new Pair<>(Long.class, "id");
    private Pair<String> uuid = new Pair<>(String.class, "uuid");
    private Pair<Long> typeId = new Pair<>(Long.class, "typeid");
    private Pair<String> name = new Pair<>(String.class, "name");
    private Pair<Boolean> active = new Pair<>(Boolean.class, "active");
    @JsonIgnore
    private Pair<String> lastErrorString = new Pair<>(String.class, "lasterror");
    @JsonIgnore
    private ServiceError lastError;

    public void setLastError(ServiceError lastError) {
        this.lastError = lastError;
        if (lastError != null) {
            N.oneLine(() -> lastErrorString.v(SerializableEntitySerializer.serialize(lastError)));
        }
    }

    public ServiceError getLastError() {
        return lastError;
    }

    private Boolean running;

    public Service() {
        init();
    }

    @Override
    public String getTableName() {
        return "service";
    }

    @Override
    protected void init() {
        populateInsert(uuid, typeId, name, active, lastErrorString);
        populateAll(id);
        lastErrorString.setSetListener(value -> {
            if (value != null) {
                N.oneLine(() -> lastError = (ServiceError) SerializableEntityDeserializer.deserialize(value));
            }
            return value;
        });
    }


    public Service setRunning(boolean running) {
        this.running = running;
        return this;
    }

    public Pair<String> getLastErrorPair() {
        return lastErrorString;
    }

    public boolean isRunning() {
        return running;
    }

    public Pair<Long> getId() {
        return id;
    }

    public Pair<String> getUuid() {
        return uuid;
    }

    public Pair<String> getName() {
        return name;
    }

    public Service setId(Long id) {
        this.id.v(id);
        return this;
    }

    public Service setName(String name) {
        this.name.v(name);
        return this;
    }

    public Service setUuid(String uuid) {
        this.uuid.v(uuid);
        return this;
    }

    public Pair<Long> getTypeId() {
        return typeId;
    }

    public Service setTypeId(Long typeId) {
        this.typeId.v(typeId);
        return this;
    }

    public Service setActive(Boolean active) {
        this.active.v(active);
        return this;
    }

    public Boolean isActive() {
        return active.v();
    }

    public Pair<Boolean> getActivePair() {
        return active;
    }
}
