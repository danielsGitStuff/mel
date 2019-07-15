package de.mein.auth.data;

import de.mein.Lok;
import de.mein.core.serialize.SerializableEntity;

/**
 * asks for more cached parts
 */
public class CachedRequest extends AbstractCachedMessage<CachedRequest> {


    private int partNumber;


    public CachedRequest setPartNumber(int partNumber) {
        // todo debug
        if (partNumber == 8)
            Lok.debug("debug");
        this.partNumber = partNumber;
        return this;
    }

    public int getPartNumber() {
        return partNumber;
    }
}
