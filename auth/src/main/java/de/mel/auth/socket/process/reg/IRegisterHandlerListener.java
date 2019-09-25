package de.mel.auth.socket.process.reg;

import de.mel.auth.data.MelRequest;
import de.mel.auth.data.db.Certificate;

/**
 * Created by xor on 4/22/16.
 */
public interface IRegisterHandlerListener {
    void onCertificateAccepted(MelRequest request, Certificate certificate);

    void onCertificateRejected(MelRequest request, Certificate certificate);
}
