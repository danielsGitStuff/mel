package de.mein.auth.socket;

import de.mein.DeferredRunnable;
import de.mein.Lok;
import de.mein.auth.MeinStrings;
import de.mein.auth.jobs.BlockReceivedJob;
import de.mein.auth.jobs.Job;
import de.mein.auth.jobs.ReceivedJob;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinWorker;
import de.mein.auth.tools.CountWaitLock;
import de.mein.auth.tools.N;

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
public class MeinSocket extends DeferredRunnable {
    private static Logger logger = Logger.getLogger(MeinSocket.class.getName());
    protected boolean allowIsolation = false;
    protected boolean isIsolated = false;
    private SocketWorker socketWorker;

    public MeinSocket setIsolated(boolean isolated) {
        isIsolated = isolated;
        return this;
    }

    // 128 kb + meta = 32 * 4kb + meta
//    public static final int BLOCK_SIZE = 32 * 4096 + 21;
    public static final int BLOCK_SIZE = 1024 * 4096 + 21; // performance increase with bigger blocks?

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

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + (meinAuthService == null ? "no service" : meinAuthService.getName());
    }

    public boolean isOpen() {
        return socket != null && !socket.isClosed();
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
        String name = "THREAD." + (meinAuthService == null ? "no service" : meinAuthService.getName()) + "." + "id=" + v + "." + getClass().getSimpleName();
        this.thread.setName(name);
        Lok.debug("MeinSocket.starting: " + name);
        this.thread.start();
    }

    private static String TOO_LONG_APPENDIX = "?";
    private static int MAX_CHARS = 63 * 1024 - 2 - TOO_LONG_APPENDIX.length();

    public void send(String json) {
        try {
            Lok.debug("   " + (meinAuthService == null ? "no service" : meinAuthService.getName()) + ".MeinSocket.send: " + json);
            if (socket.isClosed())
                Lok.error(getClass().getSimpleName() + ".send(): Socket closed!");
            while (json.length() > MAX_CHARS) {
                String s = json.substring(0, MAX_CHARS) + TOO_LONG_APPENDIX;
                json = json.substring(MAX_CHARS, json.length());
                this.out.writeUTF(s);
                this.out.flush();
            }
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

        void onBlockReceived(BlockReceivedJob block);
    }


    public MeinSocket(MeinAuthService meinAuthService, Socket socket) {
        this(meinAuthService);
        this.socket = socket;
        streams();
    }

    public MeinSocket(MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
        v = vv.getAndIncrement();
        if (meinAuthService != null)
            meinAuthService.addMeinSocket(this);
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


    @Override
    public void onShutDown() {
        N.r(() -> {
            Lok.error(getClass().getName() + ".onShutDown()");
            socketWorker.shutDown();
        });
        N.r(() -> socket.close());
    }

    StringBuffer msgBuffer = new StringBuffer();

    private CountWaitLock queueLock = new CountWaitLock();

    @Override
    public void runImpl() {
        Thread thread = Thread.currentThread();
        try {
            if (socket == null) {
                socket = socketFactory.createSocket();
                socket.connect(new InetSocketAddress(address, port));
            }
            if (in == null || out == null)
                streams();
            socketWorker = new SocketWorker(this, listener);
            meinAuthService.execute(socketWorker);
            while (!isStopped()) {
                if (isIsolated && allowIsolation) {
                    byte[] bytes = new byte[BLOCK_SIZE];
                    in.readFully(bytes);
                    BlockReceivedJob blockReceivedJob = new BlockReceivedJob().setBlock(bytes);
                    blockReceivedJob.getPromise().done(result -> queueLock.unlock());
                    socketWorker.addJob(blockReceivedJob);
                    queueLock.lock();
                } else {
                    String s = in.readUTF();
                    int l = s.length();
                    if (s.length() > MAX_CHARS) {
                        if (s.endsWith(TOO_LONG_APPENDIX)) {
                            msgBuffer.append(s.substring(0, MAX_CHARS));
                            continue;
                        } else {
                            Lok.error("strange stuff happened");
                        }
                        Lok.debug("MeinSocket.runImpl");
                    } else {
                        if (msgBuffer.length() > 0) {
                            msgBuffer.append(s);
                            socketWorker.addJob(new ReceivedJob().setMessage(msgBuffer.toString()));
                            msgBuffer = new StringBuffer();
                            continue;
                        }
                    }
                    Lok.debug("   " + meinAuthService.getName() + ".MeinSocket.runTry.got(" + socket.getInetAddress() + "): " + s);
                    if (s.equals(MeinStrings.msg.MODE_ISOLATE) && allowIsolation) {
                        if (!isIsolated)
                            send(MeinStrings.msg.MODE_ISOLATE);
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
            if (!isStopped()) {
                String line = (meinAuthService == null ? "no service" : meinAuthService.getName()) + "." + getClass().getSimpleName() + "." + socket.getClass().getSimpleName() + ".runTry.disconnected(interrupted? " + thread.isInterrupted() + ")";
                //todo debug
                if (line.startsWith("MA2.MeinAuthSocket.SSLSocketImpl.run") || line.startsWith("MA1.MeinAuthSocket.SSLSocketImpl.run"))
                    Lok.debug("MeinSocket.runImpl.943f938fw0io34");
                Lok.error(line);
                onSocketClosed(e);
                e.printStackTrace();
            }
        } finally {
            Lok.debug(getClass().getSimpleName() + " closing everything on " + Thread.currentThread().getName());
            N.s(() -> in.close());
            N.s(() -> out.close());
            N.s(() -> socket.close());
            N.s(() -> socketWorker.shutDown());
        }
    }

    protected void onSocketClosed(Exception e) {
        shutDown();
    }

    public MeinSocket setListener(MeinSocketListener listener) {
        this.listener = listener;
        return this;
    }

    public void stop() {
        try {
            Lok.debug(getClass().getSimpleName() + ".stop() on " + Thread.currentThread().getName());
//            if (this.thread != null) {
                in.close();
                out.close();
                queueLock.unlock();
                this.thread.interrupt();
                if (socketWorker!=null){
                    socketWorker.onShutDown();
                }
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
