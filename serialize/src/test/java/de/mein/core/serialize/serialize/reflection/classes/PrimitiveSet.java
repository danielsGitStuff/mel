package de.mein.core.serialize.serialize.reflection.classes;

import java.util.HashSet;
import java.util.Set;

import de.mein.core.serialize.SerializableEntity;

public class PrimitiveSet implements SerializableEntity {
    private Set<String> strings = new HashSet<>();
    private Set<Integer> ints = new HashSet<>();

    public PrimitiveSet addString(String s) {
        strings.add(s);
        return this;
    }

    public PrimitiveSet addInt(int i) {
        ints.add(i);
        return this;
    }

}