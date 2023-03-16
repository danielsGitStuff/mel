package de.mel.sql;

import de.mel.core.serialize.JsonIgnore;

import java.util.*;
import java.util.stream.Collectors;

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
    Pair<Integer> a = new Pair<>(Integer.class, "a");
    Pair<Integer> b = new Pair<>(Integer.class, "b");

    public abstract String getTableName();

    public List<Pair<?>> getAllAttributes() {
        return allAttributes;
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

    protected void addToList(List<Pair<?>> list, Pair... pairs) {
        for (Pair pair : pairs) {
            list.add(pair);
        }
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

    /**
     * print difference to sys out. Compares allAttributes only!
     *
     * @param other
     */
    public void printDifference(SQLTableObject other) {
        Comparator<Pair> pairComparator = Comparator.comparing(Pair::k);
        Map<String, Pair<?>> otherPairMap = new HashMap<>();
        List<Pair<?>> allAttributes = this.allAttributes.stream().sorted(pairComparator).collect(Collectors.toList());
        List<Pair<?>> otherAttributes = other.allAttributes.stream().sorted(pairComparator).collect(Collectors.toList());
        otherAttributes.forEach(pair -> otherPairMap.put(pair.k(), pair));
        boolean identical = true;
        for (Pair<?> pair : allAttributes) {
            if (otherPairMap.containsKey(pair.k())) {
                if (!pair.equalsValue(otherPairMap.get(pair.k()).v())) {
                    System.out.println("SQLTableObject.printDifference: att '" + pair.k() + "' differs: '" + pair.v() + "' vs '" + otherPairMap.get(pair.k()).v() + "'");
                    identical = false;
                }
                otherPairMap.remove(pair.k());
            } else {
                System.out.println("SQLTableObject.printDifference: other has no attribute '" + pair.k() + "'");
                identical = false;
            }
        }
        for (Pair<?> otherPair : otherAttributes.stream().filter(pair -> otherPairMap.containsKey(pair.k())).collect(Collectors.toList())) {
            System.out.println("SQLTableObject.printDifference: other has additional att '" + otherPair.k() + "' with value '" + otherPair.v() + "'");
            identical = false;
        }
        if (identical) {
            System.out.println("SQLTableObject.printDifference: identical");
        }
    }

    @Override
    public int hashCode() {
        int result = 0;
        if (allAttributes != null)
            for (Pair<?> pair : allAttributes) {
                result = 31 * result + pair.hashCode();
            }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SQLTableObject that = (SQLTableObject) o;
        if ((that.allAttributes == null) != (allAttributes == null))
            return false;
        if (allAttributes != null) {
            if (that.allAttributes.size() != allAttributes.size())
                return false;
            for (int i = 0; i < allAttributes.size(); i++) {
                if (!Objects.equals(that.allAttributes.get(i), allAttributes.get(i)))
                    return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(this.getClass().toString() + ".toString()");
        builder.append("\n");
        if (allAttributes != null)
            for (Pair<?> pair : allAttributes) {
                builder.append(pair.k());
                builder.append(" : ");
                builder.append(pair.valueAsString());
                builder.append("\n");
            }
        return builder.toString();
    }
}
