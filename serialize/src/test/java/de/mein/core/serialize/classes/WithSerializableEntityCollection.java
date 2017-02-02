package de.mein.core.serialize.classes;


import de.mein.core.serialize.SerializableEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 26.10.2015.
 */
public class WithSerializableEntityCollection implements SerializableEntity {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<SerializableEntity> entityserializables = new ArrayList();
    public String primitive = "primitive";
}
