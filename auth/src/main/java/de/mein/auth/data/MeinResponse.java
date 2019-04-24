package de.mein.auth.data;

import de.mein.auth.data.db.Certificate;

/**
 * Created by xor on 4/28/16.
 */
public class MeinResponse extends StateMsg {
    private Long responseId;
    private Boolean authenticated;
    private Certificate certificate;

    public MeinResponse() {

    }


    public Long getResponseId() {
        return responseId;
    }

    public MeinResponse setResponseId(Long responseId) {
        this.responseId = responseId;
        return this;
    }


    public MeinResponse setPayLoad(ServicePayload payLoad) {
        return (MeinResponse) super.setPayLoad(payLoad);
    }

    public MeinResponse setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        return this;
    }

    public MeinResponse setCertificate(Certificate certificate) {
        this.certificate = certificate;
        return this;
    }

    public Certificate getCertificate() {
        return certificate;
    }

    public MeinResponse setState(String state) {
        this.state = state;
        return this;
    }


}
