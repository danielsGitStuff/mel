package de.mein.auth.jobs;

import de.mein.auth.socket.MeinSocket;
import de.mein.auth.socket.process.val.MeinValidationProcess;

/**
 * Created by xor on 05.09.2016.
 */
public class ConnectJob extends AConnectJob<MeinValidationProcess,Void> {

    public ConnectJob(Long certificateId, String address, int port, int portCert, boolean regOnUnknown) {
        super(certificateId, address, port, portCert, regOnUnknown);
    }
}
