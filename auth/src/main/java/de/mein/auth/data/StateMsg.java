package de.mein.auth.data;

import java.io.Serializable;

import de.mein.auth.socket.MeinProcess;
import de.mein.core.serialize.SerializableEntity;

/**
 * Created by xor on 5/1/16.
 */
public abstract class StateMsg implements SerializableEntity {

    protected String state = MeinProcess.STATE_OK;
    protected IPayload payload;


    public String getState() {
        return state;
    }

    public IPayload getPayload() {
        return payload;
    }
    public StateMsg setPayLoad(IPayload payLoad) {
        this.payload = payLoad;
        return this;
    }

    public StateMsg setState(String state) {
        this.state = state;
        return this;
    }
}
