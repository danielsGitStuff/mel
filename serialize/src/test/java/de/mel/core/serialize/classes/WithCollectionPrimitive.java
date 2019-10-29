package de.mel.core.serialize.classes;


import de.mel.core.serialize.SerializableEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 26.10.2015.
 */
public class WithCollectionPrimitive implements SerializableEntity {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public  List<String> strings = new ArrayList();
    public String primitive = "primitive";

    public WithCollectionPrimitive() {
    }
}
