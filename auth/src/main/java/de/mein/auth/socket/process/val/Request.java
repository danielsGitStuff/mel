package de.mein.auth.socket.process.val;

import de.mein.auth.data.IPayload;
import de.mein.auth.data.db.Certificate;

import org.jdeferred.impl.DeferredObject;

/**
 * Created by xor on 5/1/16.
 */
public class Request<T extends IPayload> extends DeferredObject<T, Exception, Void> {
    private T payload;
    private Certificate partnerCertificate;
    private String intent;

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


    public Request<T> setIntent(String intent) {
        this.intent = intent;
        return this;
    }

    public String getIntent() {
        return intent;
    }

    public boolean hasIntent(String intent) {
        if (this.intent == null)
            return false;
        return this.intent.equals(intent);
    }
}
