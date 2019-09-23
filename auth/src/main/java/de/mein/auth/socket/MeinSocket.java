package de.mein.auth.socket;

import de.mein.DeferredRunnable;
import de.mein.Lok;
import de.mein.auth.MeinStrings;
import de.mein.auth.jobs.BlockReceivedJob;
import de.mein.auth.jobs.ReceivedJob;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.CountWaitLock;
import de.mein.auth.tools.N;
import org.jdeferred.impl.DeferredObject;

import javax.net.SocketFactory;
import javax.net.ssl.SSLException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Created by xor on 09.08.2016.
 */
public class MeinSocket extends DeferredRunnable {


//    public MeinSocket setConnectJob(AConnectJob connectJob) {
//        this.connectJob = connectJob;
//        return this;
//    }


    protected boolean allowIsolation = false;
    protected boolean isIsolated = false;
    private SocketWorker socketWorker;
    protected String runnableName = null;

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

    public void setRunnableName(String runnableName) {
        this.runnableName = runnableName;
    }

    @Override
    public String getRunnableName() {
        if (this.runnableName != null)
            return runnableName;
        return "MeinSocket for " + meinAuthService.getName();
    }

    public boolean isOpen() {
        return socket != null && !socket.isClosed();
    }

    public String getAddress() {
        if (socket != null && socket.isConnected())
            return socket.getInetAddress().getHostAddress();
        Lok.error("address requested but currently not present!");
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
        this.thread.start();
    }

    private static String TOO_LONG_APPENDIX = "?";
    private static int MAX_CHARS = 63 * 1024 - 2 - TOO_LONG_APPENDIX.length();

    public void send(String json) {
        try {
            Lok.debug("   " + (meinAuthService == null ? "no service" : meinAuthService.getName()) + ".MeinSocket(v=" + v + ").send to " + socket.getInetAddress().toString() + ": " + json);
            if (socket.isClosed()) {
                Lok.error(getClass().getSimpleName() + ".send(): Socket closed!");
                this.stop();
            }
//            Lok.debug("send(v=" + v + "): " + json);
            while (json.length() > MAX_CHARS) {
                String s = json.substring(0, MAX_CHARS) + TOO_LONG_APPENDIX;
                json = json.substring(MAX_CHARS, json.length());
                this.out.writeUTF(s);
                this.out.flush();
            }
            this.out.writeUTF(json);
            this.out.flush();
        } catch (IOException e) {
            Lok.error("MeinSocket.send.error: " + e.toString() + " v=" + v);
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


    public MeinSocket(MeinAuthService meinAuthService, Socket socket) {
        this(meinAuthService);
        this.socket = socket;
        if (meinAuthService != null)
            meinAuthService.addMeinSocket(this);
        streams();
    }

    public MeinSocket(MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
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
    public DeferredObject<Void, Void, Void> onShutDown() {
        N.r(() -> {
            if (socketWorker != null)
                socketWorker.shutDown();
        });
        N.s(() -> socket.close());
        return DeferredRunnable.ResolvedDeferredObject();
    }

    StringBuffer msgBuffer = new StringBuffer();

    private CountWaitLock queueLock = new CountWaitLock();


    @Override
    public void runImpl() {
        Thread thread = Thread.currentThread();
        if (socket == null)
            Lok.error(getClass().getSimpleName() + " running without underlying socket!!!");
        try {
            if (in == null || out == null)
                streams();
            socketWorker = new SocketWorker(this, listener);
            meinAuthService.execute(socketWorker);
            while (!isStopped()) {
                if (isIsolated && allowIsolation) {
                    byte[] bytes = new byte[BLOCK_SIZE];
                    in.readFully(bytes);
                    BlockReceivedJob blockReceivedJob = new BlockReceivedJob().setBlock(bytes);
                    blockReceivedJob.done(result -> queueLock.unlock());
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
                    Lok.debug("   " + meinAuthService.getName() + "(v=" + v + ")got(" + socket.getInetAddress() + "): " + s);
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
//            Lok.debug("MeinSocket.runTry.CLOSING");
            listener.onClose(42, "don't know shit", true);
        } catch (SSLException e) {
            String line = (meinAuthService == null ? "no service" : meinAuthService.getName()) + "." + getClass().getSimpleName() + "." + socket.getClass().getSimpleName() + ".runTry.disconnected(interrupted? " + thread.isInterrupted() + ")";
            Lok.error(line);
            Lok.error("SSLException: " + e.toString() + " v=" + v);
        } catch (Exception e) {
            if (!isStopped()) {
                String line = (meinAuthService == null ? "no service" : meinAuthService.getName()) + "." + getClass().getSimpleName() + "." + socket.getClass().getSimpleName() + ".runTry.disconnected(interrupted? " + thread.isInterrupted() + ")";
                Lok.error(line);
                Lok.error("Exception: " + e.toString() + " v=" + v);
            }
        } finally {
//            Lok.debug(getClass().getSimpleName() + " closing everything on " + Thread.currentThread().getName());
            N.r(this::onSocketClosed);
            shutDown();
        }
    }

    public int getV() {
        return v;
    }

    protected void onSocketClosed() {
        Lok.debug("closing v=" + v);
        shutDown();
    }

    public MeinSocket setListener(MeinSocketListener listener) {
        this.listener = listener;
        return this;
    }

    public void stop() {
        try {
            super.stop();
            stopped = true;
            N.s(() -> in.close());
            N.s(() -> out.close());
            N.s(() -> socket.close());
            if (socketWorker != null) {
                socketWorker.shutDown();
            }
            shutDown();
//            N.s(() -> this.thread.interrupt());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
