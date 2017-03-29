package de.mein.auth.jobs;

import de.mein.auth.data.db.Certificate;
import org.jdeferred.impl.DeferredObject;

/**
 * Created by xor on 05.09.2016.
 */
public abstract class Job<R,F,P> {
    private DeferredObject<R, F, Void> promise = new DeferredObject<>();

    public DeferredObject<R, F, Void> getPromise() {
        return promise;
    }

    public Job setPromise(DeferredObject<R, F, Void> promise) {
        this.promise = promise;
        return this;
    }

    private static class Base extends Job{
        protected Certificate partnerCertificate;

        private Base(Certificate partnerCertificate) {
            this.partnerCertificate = partnerCertificate;
        }

        public Certificate getPartnerCertificate() {
            return partnerCertificate;
        }
    }

    /**
     * Created by xor on 9/26/16.
     */
    public static class ConnectionAuthenticatedJob extends Base {

        public ConnectionAuthenticatedJob(Certificate partnerCertificate) {
            super(partnerCertificate);
        }
    }

    public static class CertificateSpottedJob extends Base {

        public CertificateSpottedJob(Certificate partnerCertificate) {
            super(partnerCertificate);
        }
    }
}
