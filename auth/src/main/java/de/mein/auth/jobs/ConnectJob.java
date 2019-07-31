package de.mein.auth.jobs;

import de.mein.Lok;
import de.mein.auth.socket.MeinValidationProcess;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by xor on 05.09.2016.
 */
public class ConnectJob extends AConnectJob<MeinValidationProcess, Void> {

    private final boolean regOnUnknown;


    public ConnectJob(Long certificateId, String address, int port, int portCert, boolean regOnUnknown) {
        super(certificateId, address, port, portCert);
        this.regOnUnknown = regOnUnknown;
    }

    public boolean getRegOnUnknown() {
        return regOnUnknown;
    }

}
