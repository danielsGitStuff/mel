package de.mein.auth.socket;

import de.mein.auth.jobs.BlockReceivedJob;
import de.mein.auth.jobs.Job;
import de.mein.auth.jobs.ReceivedJob;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinWorker;

import javax.net.SocketFactory;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by xor on 09.08.2016.
 */
public class MeinSocket implements Runnable {
    private static Logger logger = Logger.getLogger(MeinSocket.class.getName());
    protected boolean allowIsolation = false;
    protected boolean isIsolated = false;

    public MeinSocket setIsolated(boolean isolated) {
        isIsolated = isolated;
        return this;
    }

    public static final String MODE_ISOLATE = "isolate";

    // 64 * 4kb + meta
    public static final int BLOCK_SIZE = 16 * 4096 + 21;
    protected MeinAuthService meinAuthService;
    private MeinThread thread;
    protected DataOutputStream out;
    protected DataInputStream in;
    protected SocketFactory socketFactory;
    protected String address;
    protected int port;
    protected Socket socket;
    private MeinSocketListener listener;
    private final int v;
    private static AtomicInteger vv = new AtomicInteger(0);

    public MeinSocket setSocketFactory(SocketFactory socketFactory) {
        this.socketFactory = socketFactory;
        return this;
    }

    public MeinAuthService getMeinAuthService() {
        return meinAuthService;
    }

    static class MeinThread extends Thread {

        public MeinThread(MeinSocket meinSocket) {
            super(meinSocket);
        }

        @Override
        public void interrupt() {
            super.interrupt();
        }
    }

    public MeinSocket allowIsolation() {
        this.allowIsolation = true;
        return this;
    }

    public void start() {
        this.thread = new MeinThread(this);
        String name = "THREAD." + meinAuthService.getName() + "." + "id=" + v + "." + getClass().getSimpleName();
        this.thread.setName(name);
        System.out.println("MeinSocket.starting: " + name);
        this.thread.start();
    }

    public void send(String json) {
        try {
            this.out.writeUTF(json);
            this.out.flush();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "MeinSocket.send.error");
            e.printStackTrace();
        }
    }

    public MeinSocket setAddress(String address) {
        this.address = address;
        return this;
    }

    public MeinSocket setPort(int port) {
        this.port = port;
        return this;
    }

    public interface MeinSocketListener {
        void onIsolated();

        void onMessage(MeinSocket meinSocket, String msg);

        void onOpen();

        void onError(Exception ex);

        void onClose(int code, String reason, boolean remote);

        void onBlockReceived(byte[] block);
    }


    public MeinSocket(MeinAuthService meinAuthService, Socket socket) {
        this(meinAuthService);
        this.socket = socket;
        streams();
    }

    public MeinSocket(MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
        v = vv.getAndIncrement();
    }


    private void streams() {
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public MeinSocket setSocket(Socket socket) {
        this.socket = socket;
        streams();
        return this;
    }

    static class SocketWorker extends MeinWorker {

        private final MeinSocketListener listener;
        private final MeinSocket socket;

        SocketWorker(MeinSocket socket, MeinSocketListener listener) {
            this.socket = socket;
            this.listener = listener;
        }

        @Override
        protected void workWork(Job job) throws Exception {
            if (job instanceof ReceivedJob) {
                ReceivedJob receivedJob = (ReceivedJob) job;
                listener.onMessage(socket, receivedJob.getMessage());
            } else if (job instanceof BlockReceivedJob) {
                listener.onBlockReceived(((BlockReceivedJob) job).getBlock());
            }
        }

        @Override
        public String getRunnableName() {
            return getClass().getSimpleName() + " for " + socket.getMeinAuthService().getName();
        }
    }


    @Override
    public void run() {
        Thread thread = Thread.currentThread();
        try {
            if (socket == null) {
                socket = socketFactory.createSocket();
                socket.connect(new InetSocketAddress(address, port));
            }
            if (in == null || out == null)
                streams();
            SocketWorker socketWorker = new SocketWorker(this, listener);
            meinAuthService.execute(socketWorker);
            while (!Thread.currentThread().isInterrupted()) {
                if (isIsolated && allowIsolation) {
                    byte[] bytes = new byte[BLOCK_SIZE];
                    in.readFully(bytes);
                    socketWorker.addJob(new BlockReceivedJob().setBlock(bytes));
                } else {
                    String s = in.readUTF();
                    System.out.println(meinAuthService.getName() + ".MeinSocket.runTry.got: " + s);
                    if (s.equals(MODE_ISOLATE) && allowIsolation) {
                        if (!isIsolated)
                            send(MODE_ISOLATE);
                        else
                            listener.onIsolated();
                        isIsolated = true;
                    } else
                        socketWorker.addJob(new ReceivedJob().setMessage(s));
                }
            }
            logger.log(Level.SEVERE, "MeinSocket.runTry.CLOSING");
            listener.onClose(42, "don't know shit", true);
        } catch (Exception e) {
            System.err.println(meinAuthService.getName() + "." + getClass().getSimpleName() + "." + socket.getClass().getSimpleName() + ".runTry.disconnected(interrupted? " + thread.isInterrupted() + ")");
            onSocketClosed(e);
            e.printStackTrace();
        } finally {
            try {
                in.close();
                out.close();
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    protected void onSocketClosed(Exception e) {

    }

    public MeinSocket setListener(MeinSocketListener listener) {
        this.listener = listener;
        return this;
    }

    public void stop() {
        try {
            if (this.thread != null) {
                in.close();
                out.close();
                this.thread.interrupt();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
