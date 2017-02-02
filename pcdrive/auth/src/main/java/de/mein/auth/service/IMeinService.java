package de.mein.auth.service;

import de.mein.auth.data.IPayload;
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
     * @throws Exception
     */
    void handleRequest(Request request) throws Exception;

    /**
     * Messages do not require answers
     *  @param payload            data you may be interested in
     * @param partnerCertificate who sent this to you
     * @param intent
     */
    void handleMessage(IPayload payload, Certificate partnerCertificate, String intent);

    void connectionAuthenticated(Certificate partnerCertificate);

    void handleCertificateSpotted(Certificate partnerCertificate);

    void setUuid(String uuid);

    String getUuid();

    void onIsolatedConnectionEstablished(MeinIsolatedProcess isolatedProcess);

}
