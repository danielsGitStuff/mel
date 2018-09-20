package de.mein.auth.data.db;

import de.mein.core.serialize.SerializableEntity;
import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 2/29/16.
 */
public class Service extends SQLTableObject implements SerializableEntity {
    private Pair<Long> id = new Pair<>(Long.class, "id");
    private Pair<String> uuid = new Pair<>(String.class, "uuid");
    private Pair<Long> typeId = new Pair<>(Long.class, "typeid");
    private Pair<String> name = new Pair<>(String.class, "name");
    private Pair<Boolean> active = new Pair<>(Boolean.class,"active");
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
        populateInsert(uuid, typeId, name, active);
        populateAll(id);
    }

    public Service setRunning(boolean running) {
        this.running = running;
        return this;
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
