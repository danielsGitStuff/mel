package de.mein.auth.service;

import de.mein.auth.MeinNotification;
import de.mein.auth.data.ServicePayload;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess;
import de.mein.auth.socket.process.val.Request;

/**
 * Created by xor on 9/26/16.
 */
public interface IMeinService {
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
    void onIsolatedConnectionEstablished(MeinIsolatedProcess isolatedProcess);

    /**
     * Isolated connections are used to transfer files
     * @param isolatedProcess
     */
    void onIsolatedConnectionClosed(MeinIsolatedProcess isolatedProcess);

    void onServiceRegistered();

    MeinNotification createSendingNotification();

    void onCommunicationsDisabled();

    void onCommunicationsEnabled();

    void onBootLevel2Finished();

    void onBootLevel1Finished();

}
