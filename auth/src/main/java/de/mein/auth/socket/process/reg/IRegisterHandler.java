package de.mein.auth.socket.process.reg;

import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.db.Certificate;

/**
 * Created by xor on 4/22/16.
 */
public interface IRegisterHandler {
    void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate);
    void onRegistrationCompleted(Certificate partnerCertificate);

    void onRemoteRejected(Certificate partnerCertificate);

    void onLocallyRejected(Certificate partnerCertificate);

    void onRemoteAccepted(Certificate partnerCertificate);

    void onLocallyAccepted(Certificate partnerCertificate);
}
