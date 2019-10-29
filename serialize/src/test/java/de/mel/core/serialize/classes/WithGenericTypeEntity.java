package de.mel.core.serialize.classes;

import de.mel.core.serialize.SerializableEntity;

public class WithGenericTypeEntity<ChildType extends SerializableEntity> implements SerializableEntity {
    public ChildType child;
}
