package de.mein.sql;

import de.mein.core.serialize.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

/**
 * basically represents a table in a database.
 *
 * @author xor
 */
public abstract class SQLTableObject {

    @JsonIgnore
    protected transient ArrayList<Pair<?>> allAttributes;
    @JsonIgnore
    protected transient ArrayList<Pair<?>> insertAttributes;

    public abstract String getTableName();

    public List<Pair<?>> getAllAttributes() {
        return allAttributes;
    }

    protected void addToList(ArrayList<Pair<?>> list, Pair... pairs) {
        for (Pair pair : pairs) {
            list.add(pair);
        }
    }

    /**
     * put everything in here which is not auto filled by the database. afterwards insert the rest with populateAll()
     * @param pairs
     */
    protected void populateInsert(Pair... pairs) {
        if (insertAttributes == null)
            insertAttributes = new ArrayList<>();
        addToList(insertAttributes, pairs);
    }

    /**
     * insert IDs and other stuff that is auto filled by the database. call populateInsert() first
     * @param pairs
     */
    protected void populateAll(Pair... pairs) {
        allAttributes = new ArrayList<>(insertAttributes);
        if (pairs != null && pairs.length > 0)
            addToList(allAttributes, pairs);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(this.getClass().toString() + ".toString()");
        builder.append("\n");
        for (Pair<?> pair : allAttributes) {
            builder.append(pair.k());
            builder.append(" : ");
            builder.append(pair.valueAsString());
            builder.append("\n");
        }
        return builder.toString();
    }

    public List<Pair<?>> getInsertAttributes() {
        return insertAttributes;
    }

    /**
     * allAttributes, fewAttributes, insertAttributes should be filled here. it
     * is recommended to call this method in the constructor.
     */
    protected abstract void init();

    public int getHashCode() {
        int result = 0;
        for (Pair<?> pair : allAttributes) {
            result += pair.hashCode();
        }
        return result;
    }

}
