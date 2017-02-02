package de.mein.auth.socket.process.reg;

import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.db.Certificate;

/**
 * Created by xor on 4/22/16.
 */
public interface IRegisterHandlerListener {
    void onCertificateAccepted(MeinRequest request, Certificate certificate);

    void onCertificateRejected(MeinRequest request, Certificate certificate);
}
