package de.mel.auth.jobs;

import de.mel.auth.data.db.Certificate;
import de.mel.auth.tools.CountWaitLock;

import org.jdeferred.impl.DeferredObject;

/**
 * When a message/request requires (probably long) work to do you may use Jobs so the Socket thread does not block for all that time.
 */
public abstract class Job<R,F,P> extends DeferredObject<R,F,Void>{

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
