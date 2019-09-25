package de.mel.auth.data.db;

import de.mel.sql.Pair;
import de.mel.sql.SQLTableObject;

/**
 * Created by xor on 4/27/16.
 */
public class ServiceType extends SQLTableObject {
    private Pair<Long> id = new Pair<>(Long.class, "id");
    private Pair<String> type = new Pair<String>(String.class, "type");

    public Pair<String> getDescription() {
        return description;
    }

    public ServiceType(){
        init();
    }

    public ServiceType setDescription(String description) {
        this.description.v(description);
        return this;
    }

    public Pair<Long> getId() {
        return id;
    }

    public ServiceType setId(Long id) {
        this.id.v(id);
        return this;
    }

    public Pair<String> getType() {
        return type;
    }

    public ServiceType setType(String type) {
        this.type.v(type);
        return this;
    }

    private Pair<String> description = new Pair<String>(String.class, "description");

    @Override
    public String getTableName() {
        return "servicetype";
    }

    @Override
    protected void init() {
        populateInsert(type, description);
        populateAll(id );
    }
}
