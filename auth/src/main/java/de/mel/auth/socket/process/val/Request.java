package de.mel.auth.socket.process.val;

import de.mel.auth.data.ServicePayload;
import de.mel.auth.data.db.Certificate;

import org.jdeferred.impl.DeferredObject;

/**
 * Created by xor on 5/1/16.
 */
public class Request<T extends ServicePayload> extends DeferredObject<T, Exception, Void> {
    private T payload;
    private Certificate partnerCertificate;
    private String serviceUuid;

    public Request setPayload(T payload) {
        this.payload = payload;
        return this;
    }

    public Request<T> setPartnerCertificate(Certificate partnerCertificate) {
        this.partnerCertificate = partnerCertificate;
        return this;
    }

    public Certificate getPartnerCertificate() {
        return partnerCertificate;
    }

    public T getPayload() {
        return payload;
    }

    public boolean hasIntent(String intentQuery) {
        return payload != null && payload.hasIntent(intentQuery);
    }

    public Request setServiceUuid(String serviceUuid) {
        this.serviceUuid = serviceUuid;
        return this;
    }
}
