package de.mel.core.serialize.classes;

import de.mel.core.serialize.SerializableEntity;

import java.util.ArrayList;
import java.util.List;

public class WithCollectionGenericType<T extends SerializableEntity> implements SerializableEntity {
    public List<T> list = new ArrayList<>();
}
