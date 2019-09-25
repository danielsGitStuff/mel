package de.mel.drive.data;

import de.mel.drive.sql.Stage;
import de.mel.sql.Pair;
import de.mel.sql.SQLTableObject;

public class UnorderedStagePair extends SQLTableObject {
    private Stage stage = new Stage();
    private Pair<Long> bigOrder = new Pair<>(Long.class, "bord");
    private Pair<Long> smallOrder = new Pair<>(Long.class, "sord");
    private Pair<Long> bigId = new Pair<>(Long.class, "bid");
    private Pair<Long> smallId = new Pair<>(Long.class, "sid");

    public Pair<Long> getBigOrder() {
        return bigOrder;
    }

    public Pair<Long> getSmallOrder() {
        return smallOrder;
    }

    public Pair<Long> getBigId() {
        return bigId;
    }

    public Pair<Long> getSmallId() {
        return smallId;
    }

    public UnorderedStagePair() {
        init();
    }

    @Override
    public String getTableName() {
        return stage.getTableName();
    }

    @Override
    protected void init() {
        populateInsert(bigId, bigOrder, smallId, smallOrder);
        populateAll();
    }
}
