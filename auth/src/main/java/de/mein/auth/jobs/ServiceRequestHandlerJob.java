package de.mein.auth.jobs;

import de.mein.auth.data.IPayload;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.socket.process.val.Request;

/**
 * Created by xor on 9/25/16.
 */
public class ServiceRequestHandlerJob extends Job {
    private Request request;
    private IPayload payload;
    private Certificate partnerCertificate;
    private String intent;

    public ServiceRequestHandlerJob() {

    }

    public ServiceRequestHandlerJob setPayload(IPayload payload) {
        this.payload = payload;
        return this;
    }

    public ServiceRequestHandlerJob setRequest(Request request) {
        this.request = request;
        this.partnerCertificate = request.getPartnerCertificate();
        return this;
    }

    public ServiceRequestHandlerJob setPartnerCertificate(Certificate partnerCertificate) {
        this.partnerCertificate = partnerCertificate;
        return this;
    }

    public Certificate getPartnerCertificate() {
        return partnerCertificate;
    }

    public Request getRequest() {
        return request;
    }

    public IPayload getPayLoad() {
        return payload;
    }

    public boolean isMessage() {
        return request == null;
    }

    public boolean isRequest() {
        return payload == null && request != null;
    }

    public ServiceRequestHandlerJob setIntent(String intent) {
        this.intent = intent;
        return this;
    }

    public String getIntent() {
        return intent;
    }
}
