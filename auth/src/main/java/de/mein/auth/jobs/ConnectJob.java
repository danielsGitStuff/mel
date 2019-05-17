package de.mein.auth.jobs;

import de.mein.Lok;
import de.mein.auth.socket.MeinValidationProcess;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by xor on 05.09.2016.
 */
public class ConnectJob extends AConnectJob<MeinValidationProcess, Void> {

    private static AtomicLong DEBUG_COUNTER = new AtomicLong(0l);
    private final long ID;
    private final boolean regOnUnknown;


    public ConnectJob(Long certificateId, String address, int port, int portCert, boolean regOnUnknown) {
        super(certificateId, address, port, portCert);
        this.regOnUnknown = regOnUnknown;
        this.ID = DEBUG_COUNTER.incrementAndGet();
        //todo debug
        if (this.ID == 1L)
            Lok.error("debug");
    }

    public boolean getRegOnUnknown() {
        return regOnUnknown;
    }

}
