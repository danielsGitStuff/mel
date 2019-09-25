package de.mel.auth.data;

import de.mel.auth.MelStrings;
import de.mel.core.serialize.SerializableEntity;

/**
 * Created by xor on 5/1/16.
 */
public abstract class StateMsg implements SerializableEntity {

    protected String state = MelStrings.msg.STATE_OK;
    protected ServicePayload payload;
    protected ResponseException exception;


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

    public ResponseException getException() {
        return exception;
    }

    public void setException(ResponseException exception) {
        this.exception = exception;
    }


    public StateMsg setState(String state) {
        this.state = state;
        return this;
    }
}
