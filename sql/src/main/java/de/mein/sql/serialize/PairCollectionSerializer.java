package de.mein.sql.serialize;

import java.util.Collection;
import java.util.Iterator;

import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializer;
import de.mein.core.serialize.serialize.tools.StringBuilder;
import de.mein.sql.Pair;

/**
 * Created by xor on 10/11/17.
 */

public class PairCollectionSerializer extends FieldSerializer {

    private final Collection<Pair> pairs;

    public PairCollectionSerializer(Collection<Pair> pairs){
        this.pairs = pairs;
    }
    @Override
    public boolean isNull() {
        return pairs == null;
    }

    @Override
    public String JSON() throws JsonSerializationException {
        StringBuilder b = new StringBuilder();
        b.arrBegin();
        Iterator iterator = pairs.iterator();
        while (iterator.hasNext()) {
            b.append(new PairSerializer((Pair<?>) iterator.next()).JSON());
            if (iterator.hasNext())
                b.comma();
        }
        b.arrEnd();
        return b.toString();
    }
}
