package de.mein.auth.socket;

import de.mein.Lok;
import de.mein.MeinRunnable;
import de.mein.auth.jobs.AConnectJob;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.N;

import java.util.Objects;

public class ConnectWorker implements MeinRunnable {

    private final AConnectJob connectJob;
    private final MeinAuthService meinAuthService;

    public ConnectWorker(MeinAuthService meinAuthService, AConnectJob connectJob) {
        Objects.requireNonNull(connectJob);
        this.meinAuthService = meinAuthService;
        this.connectJob = connectJob;
    }

    @Override
    public String getRunnableName() {
        String line = "Connecting to: " + connectJob.getAddress() + ":" + connectJob.getPort() + "/" + connectJob.getPortCert();
        return line;
    }

    @Override
    public void run() {
        Lok.debug("Connecting to: " + connectJob.getAddress() + ":" + connectJob.getPort() + "/" + connectJob.getPortCert() + "?reg=" + connectJob.getRegOnUnknown());
        MeinAuthSocket meinAuthSocket = new MeinAuthSocket(connectJob, meinAuthService);
        N.oneLine(() -> meinAuthSocket.connect(connectJob));
    }
}
