package de.mel.core.serialize.classes;

import de.mel.core.serialize.SerializableEntity;

import java.util.HashMap;
import java.util.Map;

public class WithMapGenericType<T extends SerializableEntity> implements SerializableEntity {
    public Map<String,T> entities = new HashMap<>();
}
