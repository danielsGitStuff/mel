package de.mein.auth.socket;

import de.mein.DeferredRunnable;
import de.mein.Lok;
import de.mein.auth.MeinStrings;
import de.mein.auth.jobs.AConnectJob;
import de.mein.auth.jobs.BlockReceivedJob;
import de.mein.auth.jobs.ConnectJob;
import de.mein.auth.jobs.ReceivedJob;
import de.mein.auth.service.MeinAuthService;
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

    private AConnectJob connectJob;

//    public MeinSocket setConnectJob(AConnectJob connectJob) {
//        this.connectJob = connectJob;
//        return this;
//    }

    public AConnectJob getConnectJob() {
        return connectJob;
    }

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
    protected Socket socket;
    private MeinSocketListener listener;
    private final int v;
    private static AtomicInteger vv = new AtomicInteger(0);

    public MeinAuthService getMeinAuthService() {
        return meinAuthService;
    }

    @Override
    public String getRunnableName() {
        String line = (meinAuthService == null ? "no service" : meinAuthService.getName()) + ".S";
        if (connectJob != null)
            line += "->";
        else
            line += "<-";
        line += getAddress() + "/READ";
        return line;
    }

    public boolean isOpen() {
        return socket != null && !socket.isClosed();
    }

    public String getAddress() {
        if (socket != null && socket.isConnected())
            return socket.getInetAddress().toString();
        if (connectJob != null)
            return connectJob.getAddress();
        return "#could not determine address#";
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
            if (socket == null || socket.getInetAddress() == null)
                Lok.error("bug");
            Lok.debug("   " + (meinAuthService == null ? "no service" : meinAuthService.getName()) + ".MeinSocket.send to " + socket.getInetAddress().toString() + ": " + json);
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
            Lok.error("MeinSocket.send.error");
            e.printStackTrace();
        }
    }

    public interface MeinSocketListener {
        void onIsolated();

        void onMessage(MeinSocket meinSocket, String msg);

        void onOpen();

        void onError(Exception ex);

        void onClose(int code, String reason, boolean remote);

        void onBlockReceived(BlockReceivedJob block);
    }

    protected void connectSocket(AConnectJob connectJob) throws IOException {
        socket = SocketFactory.getDefault().createSocket();
        socket.connect(new InetSocketAddress(connectJob.getAddress(), connectJob.getPort()));
    }


    public MeinSocket(MeinAuthService meinAuthService, Socket socket) {
        this(null, meinAuthService);
        this.socket = socket;
        if (meinAuthService != null)
            meinAuthService.addMeinSocket(this);
        streams();
    }

    public MeinSocket(AConnectJob connectJob, MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
        this.connectJob = connectJob;
        v = vv.getAndIncrement();
        if (meinAuthService != null)
            meinAuthService.addMeinSocket(this);
    }


    private void streams() {
        N.oneLine(() -> {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
        });
    }

    public MeinSocket setSocket(Socket socket) {
        this.socket = socket;
        streams();
        return this;
    }


    @Override
    public void onShutDown() {
        N.r(() -> {
            Lok.error(getClass().getName() + ".onShutDown()");
            if (socketWorker != null)
                socketWorker.shutDown();
        });
        N.s(() -> socket.close());
    }

    StringBuffer msgBuffer = new StringBuffer();

    private CountWaitLock queueLock = new CountWaitLock();

    //todo debug
    private static int msgCount = 0;

    @Override
    public void runImpl() {
        Thread thread = Thread.currentThread();
        try {
            if (socket == null && connectJob == null)
                Lok.error("socket has nothing to do!");

            if (socket == null && connectJob != null) {
                connectSocket(connectJob);
            }
            if (in == null || out == null)
                streams();
            socketWorker = new SocketWorker(this, listener);
            //todo debug
            if (meinAuthService.getName().equals("MA2") && socketWorker.index == 6)
                Lok.debug("delme");
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

                    msgCount++;
                    //todo debug
                    if (msgCount == 10)
                        Lok.debug("debug");
                    Lok.debug("count: " + msgCount);
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
            Lok.debug("MeinSocket.runTry.CLOSING");
            listener.onClose(42, "don't know shit", true);
        } catch (Exception e) {
            if (!isStopped()) {
                String line = (meinAuthService == null ? "no service" : meinAuthService.getName()) + "." + getClass().getSimpleName() + "." + socket.getClass().getSimpleName() + ".runTry.disconnected(interrupted? " + thread.isInterrupted() + ")";
                Lok.error(line);
            }
        } finally {
            Lok.debug(getClass().getSimpleName() + " closing everything on " + Thread.currentThread().getName());
            N.r(this::onSocketClosed);
            N.s(() -> in.close());
            N.s(() -> out.close());
            N.s(() -> socket.close());
            N.s(() -> socketWorker.shutDown());
        }
    }

    protected void onSocketClosed() {
        shutDown();
    }

    public MeinSocket setListener(MeinSocketListener listener) {
        this.listener = listener;
        return this;
    }

    public void stop() {
        try {
            Lok.debug(getClass().getSimpleName() + ".stop() on " + Thread.currentThread().getName());
            N.s(() -> in.close());
            N.s(() -> out.close());
            N.s(() -> this.thread.interrupt());
            N.s(() -> socket.close());
            queueLock.unlock();
            if (socketWorker != null) {
                socketWorker.onShutDown();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
