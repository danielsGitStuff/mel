package de.mel.auth.jobs;

import de.mel.Lok;
import de.mel.auth.socket.MelValidationProcess;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by xor on 05.09.2016.
 */
public class ConnectJob extends AConnectJob<MelValidationProcess, Void> {

    private final boolean regOnUnknown;


    public ConnectJob(Long certificateId, String address, int port, int portCert, boolean regOnUnknown) {
        super(certificateId, address, port, portCert);
        this.regOnUnknown = regOnUnknown;
    }

    public boolean getRegOnUnknown() {
        return regOnUnknown;
    }

}
