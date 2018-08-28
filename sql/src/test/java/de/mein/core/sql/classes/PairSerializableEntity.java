package de.mein.core.sql.classes;

import de.mein.core.serialize.SerializableEntity;
import de.mein.sql.Pair;

/**
 * Created by xor on 26.10.2015.
 */
public class PairSerializableEntity implements SerializableEntity {
    public Pair<String> pair = new Pair<>(String.class, "pair.key");
}
