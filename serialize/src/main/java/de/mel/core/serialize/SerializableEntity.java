package de.mel.core.serialize;

import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

/**
 * Base class for everything that can be serialized and deserialized.
 * In order for deserialization to work properly you'll have to provide an empty constructor.
 *
 * @author xor
 */
public interface SerializableEntity {

    default String toJSON() throws JsonSerializationException {
        return SerializableEntitySerializer.serialize(this);
    }
}