package de.mein.auth.data;

import de.mein.auth.MeinStrings;
import de.mein.core.serialize.SerializableEntity;

/**
 * Created by xor on 5/1/16.
 */
public abstract class StateMsg implements SerializableEntity {

    protected String state = MeinStrings.msg.STATE_OK;
    protected ServicePayload payload;


    public String getState() {
        return state;
    }

    public ServicePayload getPayload() {
        return payload;
    }
    public StateMsg setPayLoad(ServicePayload payLoad) {
        this.payload = payLoad;
        return this;
    }

    public StateMsg setState(String state) {
        this.state = state;
        return this;
    }
}
