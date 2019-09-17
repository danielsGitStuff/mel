package de.mein.auth.jobs;

import de.mein.Lok;
import de.mein.auth.socket.MeinValidationProcess;
import org.jdeferred.Deferred;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by xor on 05.09.2016.
 */
public class ConnectJob extends AConnectJob<MeinValidationProcess, Void> {

    private final boolean regOnUnknown;
    //todo debug
    private static final AtomicInteger DEBUG_COUNTER = new AtomicInteger(0);
    public final int DEBUG_ID = DEBUG_COUNTER.incrementAndGet();


    public ConnectJob(Long certificateId, String address, int port, int portCert, boolean regOnUnknown) {
        super(certificateId, address, port, portCert);
        this.regOnUnknown = regOnUnknown;
        if (DEBUG_ID == 1)
            Lok.debug();
    }

    public boolean getRegOnUnknown() {
        return regOnUnknown;
    }

    @Override
    public Deferred<MeinValidationProcess, Exception, Void> reject(Exception reject) {
        //todo debug
        if (DEBUG_ID == 1)
            Lok.debug();
        return super.reject(reject);
    }
}
