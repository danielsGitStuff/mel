package de.mein.auth.socket;

import de.mein.Lok;
import de.mein.auth.jobs.BlockReceivedJob;
import de.mein.auth.jobs.Job;
import de.mein.auth.jobs.ReceivedJob;
import de.mein.auth.service.MeinWorker;

import java.util.concurrent.atomic.AtomicInteger;

class SocketWorker extends MeinWorker {

    private final MeinSocket.MeinSocketListener listener;
    private final MeinSocket socket;
    private static final AtomicInteger INDEX = new AtomicInteger(0);
    public final int index;

    SocketWorker(MeinSocket socket, MeinSocket.MeinSocketListener listener) {
        this.socket = socket;
        this.listener = listener;
        // todo debug
        this.index = INDEX.getAndIncrement();
    }

    @Override
    protected void workWork(Job job) throws Exception {
        if (job instanceof ReceivedJob) {
            ReceivedJob receivedJob = (ReceivedJob) job;
            listener.onMessage(socket, receivedJob.getMessage());
        } else if (job instanceof BlockReceivedJob) {
            listener.onBlockReceived(((BlockReceivedJob) job));
        }
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + (socket.getMeinAuthService() == null ? "no service" : socket.getMeinAuthService().getName());
    }

    @Override
    public void onShutDown() {
        Lok.debug("SocketWorker.onShutDown, Runnable: " + getRunnableName());
        super.onShutDown();
    }

}
