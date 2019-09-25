package de.mel.drive.quota;

import de.mel.drive.sql.Waste;
import de.mel.sql.Pair;
import de.mel.sql.SQLTableObject;

/**
 * Created by xor on 10.11.2017.
 */
public class WasteDummy extends SQLTableObject {
    private Pair<Long> id;
    private Pair<Long> size;

    public WasteDummy() {

    }

    @Override
    public String getTableName() {
        return new Waste().getTableName();
    }

    @Override
    protected void init() {
        Waste waste = new Waste();
        id = new Pair<Long>(Long.class, "w." + waste.getId().k());
        size = new Pair<Long>(Long.class, "w." + waste.getSize().k());
        populateInsert();
        populateAll(id, size);
    }

    public Pair<Long> getId() {
        return id;
    }

    public Pair<Long> getSize() {
        return size;
    }
}
