package de.mein.core.sql.classes;

import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

/**
 * Created by xor on 12/6/16.
 */
public class CrashTestDummy extends SQLTableObject {
    private Pair<Long> id = new Pair<>(Long.class, "id");
    private Pair<String> name = new Pair<>(String.class, "name");

    public CrashTestDummy() {
        init();
    }

    @Override
    public String getTableName() {
        return "atest";
    }

    @Override
    protected void init() {
        populateInsert(name);
        populateAll(id);
    }

    public CrashTestDummy setId(Long id) {
        this.id.v(id);
        return this;
    }

    public CrashTestDummy setName(String name) {
        this.name.v(name);
        return this;
    }

    public Pair<Long> getId() {
        return id;
    }

    public Pair<String> getName() {
        return name;
    }
}
