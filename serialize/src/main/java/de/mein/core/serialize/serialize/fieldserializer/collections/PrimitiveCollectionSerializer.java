package de.mein.core.serialize.serialize.fieldserializer.collections;

import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializer;
import de.mein.core.serialize.serialize.tools.StringBuilder;

import java.util.Iterator;

/**
 * Created by xor on 12/12/16.
 */
public class PrimitiveCollectionSerializer extends FieldSerializer {

    private final Iterable iterable;

    public PrimitiveCollectionSerializer(Iterable iterable) {
        this.iterable = iterable;
    }

    @Override
    public boolean isNull() {
        return iterable == null;
    }

    @Override
    public String JSON() throws JsonSerializationException {
        StringBuilder b = new StringBuilder();
        b.arrBegin();
        Iterator iterator = iterable.iterator();
        while (iterator.hasNext()) {
            b.value(iterator.next());
            if (iterator.hasNext())
                b.comma();
        }
        b.arrEnd();
        return b.toString();
    }

}
