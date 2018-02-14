package de.mein.auth.data;

import de.mein.core.serialize.SerializableEntity;

public class CachedRequest extends AbstractCachedMessage<CachedRequest> {


    private int partNumber;



    public CachedRequest setPartNumber(int partNumber) {
        this.partNumber = partNumber;
        return this;
    }

    public int getPartNumber() {
        return partNumber;
    }
}
