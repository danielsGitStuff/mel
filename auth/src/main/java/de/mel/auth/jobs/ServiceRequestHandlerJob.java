package de.mel.auth.jobs;

import de.mel.auth.data.ServicePayload;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.socket.process.val.Request;

/**
 * Used to tell the service that it has to do some work
 * Created by xor on 9/25/16.
 */
public class ServiceRequestHandlerJob extends Job {
    private Request request;
    private ServicePayload payload;
    private Certificate partnerCertificate;
    private String intent;

    public ServiceRequestHandlerJob() {

    }

    public ServiceRequestHandlerJob setPayload(ServicePayload payload) {
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

    public ServicePayload getPayLoad() {
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
