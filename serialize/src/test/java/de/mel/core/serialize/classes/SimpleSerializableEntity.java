package de.mel.core.serialize.classes;

import de.mel.core.serialize.SerializableEntity;

/**
 * Created by xor on 12/21/15.
 */
public class SimpleSerializableEntity implements SerializableEntity {
    public SimpleSerializableEntity in;
    public SimpleSerializableEntity out;
    private String primitive;

    public SimpleSerializableEntity setPrimitive(String primitive) {
        this.primitive = primitive;
        return this;
    }

    public String getPrimitive() {
        return primitive;
    }
}
