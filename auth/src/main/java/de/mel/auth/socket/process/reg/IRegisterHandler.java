package de.mel.auth.socket.process.reg;

import de.mel.auth.MelAuthAdmin;
import de.mel.auth.data.MelRequest;
import de.mel.auth.data.db.Certificate;

/**
 * Created by xor on 4/22/16.
 */
public interface IRegisterHandler {
    /**
     * called when a registration request is handled. you should ask the user in an appropriate way how to handle this.
     * he can either accept or reject.
     *
     * @param listener      call onCertificateAccepted() or onCertificateRejected() accordingly
     * @param request
     * @param myCertificate
     * @param certificate
     */
    void acceptCertificate(IRegisterHandlerListener listener, MelRequest request, Certificate myCertificate, Certificate certificate);

    void onRegistrationCompleted(Certificate partnerCertificate);

    /**
     * called when the remote side rejected the request
     *
     * @param partnerCertificate
     */
    void onRemoteRejected(Certificate partnerCertificate);

    /**
     * called when some MelAuthAdmin on the current device rejected the request
     *
     * @param partnerCertificate
     */
    void onLocallyRejected(Certificate partnerCertificate);

    /**
     * called when the remote side accepted the request
     *
     * @param partnerCertificate
     */
    void onRemoteAccepted(Certificate partnerCertificate);

    /**
     * called when some MelAuthAdmin on the current device accepted the request
     *
     * @param partnerCertificate
     */
    void onLocallyAccepted(Certificate partnerCertificate);

    /**
     * called before the {@link MelAuthAdmin} asks the register handlers to do things
     *
     * @param melAuthAdmin
     */
    default void setup(MelAuthAdmin melAuthAdmin) {

    }
}
