package de.mein.core.serialize.classes;


import de.mein.core.serialize.SerializableEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 26.10.2015.
 */
public class WithPrimitiveCollection implements SerializableEntity {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public  List<String> strings = new ArrayList();
    public String primitive = "primitive";

    public WithPrimitiveCollection() {
    }
}
