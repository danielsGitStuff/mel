package de.mel.auth.socket;

import de.mel.Lok;
import de.mel.auth.jobs.AConnectJob;
import de.mel.auth.jobs.BlockReceivedJob;
import de.mel.auth.jobs.Job;
import de.mel.auth.jobs.ReceivedJob;
import de.mel.auth.service.MelWorker;
import org.jdeferred.Promise;

import java.util.concurrent.atomic.AtomicInteger;

class SocketWorker extends MelWorker {

    private final MelSocket.MelSocketListener listener;
    private final MelSocket socket;
    private static final AtomicInteger INDEX = new AtomicInteger(0);
    public final int index;

    SocketWorker(MelSocket socket, MelSocket.MelSocketListener listener) {
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
        }else if (job instanceof AConnectJob){
            Lok.debug("connect!!!");
        }
    }

    @Override
    public String getRunnableName() {
        return socket.getRunnableName()+ "/WORK";
    }

    @Override
    public Promise<Void, Void, Void> onShutDown() {
//        Lok.debug("SocketWorker.onShutDown, Runnable: " + getRunnableName());
        listener.onClose(100, "shutdown", false);
       return super.onShutDown();
    }

}
