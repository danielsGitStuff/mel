package de.mel.auth.data;

import de.mel.auth.data.db.Certificate;

/**
 * Created by xor on 4/28/16.
 */
public class MelResponse extends StateMsg {
    private Long responseId;
    private Boolean authenticated;
    private Certificate certificate;

    public MelResponse() {

    }


    public Long getResponseId() {
        return responseId;
    }

    public MelResponse setResponseId(Long responseId) {
        this.responseId = responseId;
        return this;
    }


    public MelResponse setPayLoad(ServicePayload payLoad) {
        return (MelResponse) super.setPayLoad(payLoad);
    }

    public MelResponse setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        return this;
    }

    public MelResponse setCertificate(Certificate certificate) {
        this.certificate = certificate;
        return this;
    }

    public Certificate getCertificate() {
        return certificate;
    }

    public MelResponse setState(String state) {
        this.state = state;
        return this;
    }


}
