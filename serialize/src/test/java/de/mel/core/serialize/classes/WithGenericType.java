package de.mel.core.serialize.classes;

import de.mel.core.serialize.SerializableEntity;

public class WithGenericType<T extends SimplestEntity> implements SerializableEntity {
    public T child;
}
