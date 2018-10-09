package de.mein.auth.data.db;

import de.mein.sql.Pair;
import de.mein.sql.SQLTableObject;

public class MissingHash extends SQLTableObject {
    private static final String HASH = "hash";
    private Pair<String> hash = new Pair<>(String.class, HASH);

    public MissingHash() {
        init();
    }

    public MissingHash(String hash) {
        init();
        this.hash.v(hash);
    }

    @Override
    public String getTableName() {
        return "missinghash";
    }

    @Override
    protected void init() {
        populateInsert(hash);
        populateAll();
    }

    public Pair<String> getHash() {
        return hash;
    }
}
