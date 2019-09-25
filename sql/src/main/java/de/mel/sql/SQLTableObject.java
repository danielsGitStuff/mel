package de.mel.sql;

import de.mel.core.serialize.JsonIgnore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * basically represents a row in a database table.
 *
 * @author xor
 */
public abstract class SQLTableObject {

    @JsonIgnore
    protected transient List<Pair<?>> allAttributes;
    @JsonIgnore
    protected transient List<Pair<?>> insertAttributes;
    @JsonIgnore
    protected transient Map<String, Pair<?>> pairs;

    public abstract String getTableName();

    public List<Pair<?>> getAllAttributes() {
        return allAttributes;
    }

    protected void addToList(List<Pair<?>> list, Pair... pairs) {
        for (Pair pair : pairs) {
            list.add(pair);
        }
    }

    /**
     * put everything in here which is not auto filled by the database. afterwards insert the rest with populateAll()
     *
     * @param pairs
     */
    protected void populateInsert(Pair... pairs) {
        if (insertAttributes == null)
            insertAttributes = new ArrayList<>();
        addToList(insertAttributes, pairs);
    }

    /**
     * insert IDs and other stuff that is auto filled by the database. call populateInsert() first
     *
     * @param pairs
     */
    protected void populateAll(Pair... pairs) {
        try {
            allAttributes = new ArrayList<>(insertAttributes);
            if (pairs != null && pairs.length > 0)
                addToList(allAttributes, pairs);
        } catch (NullPointerException e) {
            System.err.println("NullPointerException when calling: " + getClass().getSimpleName() + ".populateAll(). Did you forgot to call populateInsert() in advance?");
        }
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

    public Pair<?> getPair(String k) {
        if (pairs == null) {
            pairs = new HashMap<>();
            for (Pair<?> pair : allAttributes)
                pairs.put(pair.k(), pair);
        }
        return pairs.get(k);
    }
}
