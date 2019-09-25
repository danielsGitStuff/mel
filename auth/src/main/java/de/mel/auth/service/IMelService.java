package de.mel.auth.service;

import de.mel.auth.MelNotification;
import de.mel.auth.data.ServicePayload;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
import de.mel.auth.socket.process.val.Request;

/**
 * Created by xor on 9/26/16.
 */
public interface IMelService {
    /**
     * Requests want answers. You can pass the answer to the {@link Request}
     *
     * @param request reject or resolve this when done
     */
    void handleRequest(Request request) throws Exception;

    /**
     * Messages do not require answers
     *  @param payload            data you may be interested in
     * @param partnerCertificate who sent this to you
     */
    void handleMessage(ServicePayload payload, Certificate partnerCertificate);

    /**
     * there is a new connection to someone you know
     * @param partnerCertificate
     */
    void connectionAuthenticated(Certificate partnerCertificate);

    /**
     *
     * @param partnerCertificate
     */
    void handleCertificateSpotted(Certificate partnerCertificate);

    String getUuid();

    /**
     * Isolated connections are used to transfer files
     * @param isolatedProcess
     */
    void onIsolatedConnectionEstablished(MelIsolatedProcess isolatedProcess);

    /**
     * Isolated connections are used to transfer files
     * @param isolatedProcess
     */
    void onIsolatedConnectionClosed(MelIsolatedProcess isolatedProcess);

    void onServiceRegistered();

    MelNotification createSendingNotification();

    void onCommunicationsDisabled();

    void onCommunicationsEnabled();

    void onBootLevel2Finished();

    void onBootLevel1Finished();

}
