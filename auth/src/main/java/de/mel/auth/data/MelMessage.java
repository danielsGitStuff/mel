package de.mel.auth.data;

/**
 * Created by xor on 2/26/16.
 */
public class MelMessage extends StateMsg {

    protected String serviceUuid;
    protected String intent;


    public MelMessage() {

    }

    public MelMessage setState(String state) {
        this.state = state;
        return this;
    }

    public MelMessage(String serviceUuid, String intent) {
        this.serviceUuid = serviceUuid;
        this.intent = intent;
    }

    public String getIntent() {
        return intent;
    }


    public String getServiceUuid() {
        return serviceUuid;
    }


    public ServicePayload getPayload() {
        return payload;
    }



    public MelMessage setPayLoad(ServicePayload payLoad) {
        return (MelMessage) super.setPayLoad(payLoad);
    }

}
