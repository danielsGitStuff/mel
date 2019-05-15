package de.mein.auth.socket;

import de.mein.MeinRunnable;
import de.mein.auth.jobs.AConnectJob;

public class ConnectWorker implements MeinRunnable {

    private final AConnectJob connectJob;

    public ConnectWorker(AConnectJob connectJob) {
        this.connectJob = connectJob;
    }

    @Override
    public String getRunnableName() {
        return "///";
    }

    @Override
    public void run() {

    }
}
