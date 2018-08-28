package de.mein.auth.data;

/**
 * Created by xor on 2/26/16.
 */
public class MeinMessage extends StateMsg {

    protected String serviceUuid;
    protected String intent;


    public MeinMessage() {

    }

    public MeinMessage setState(String state) {
        this.state = state;
        return this;
    }

    public MeinMessage(String serviceUuid, String intent) {
        this.serviceUuid = serviceUuid;
        this.intent = intent;
    }

    public String getIntent() {
        return intent;
    }


    public String getServiceUuid() {
        return serviceUuid;
    }


    public IPayload getPayload() {
        return payload;
    }



    public MeinMessage setPayLoad(IPayload payLoad) {
        return (MeinMessage) super.setPayLoad(payLoad);
    }

}
