package de.mel.auth.socket;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.MelStrings;
import de.mel.auth.jobs.BlockReceivedJob;
import de.mel.auth.jobs.ReceivedJob;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.tools.CountWaitLock;
import de.mel.auth.tools.N;
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
public class MelSocket extends DeferredRunnable {


//    public MelSocket setConnectJob(AConnectJob connectJob) {
//        this.connectJob = connectJob;
//        return this;
//    }


    protected boolean allowIsolation = false;
    protected boolean isIsolated = false;
    private SocketWorker socketWorker;
    protected String runnableName = null;

    public MelSocket setIsolated(boolean isolated) {
        isIsolated = isolated;
        return this;
    }

    // 128 kb + meta = 32 * 4kb + meta
//    public static final int BLOCK_SIZE = 32 * 4096 + 21;
    public static final int BLOCK_SIZE = 1024 * 4096 + 21; // performance increase with bigger blocks?

    protected MelAuthService melAuthService;
    private MelThread thread;
    protected DataOutputStream out;
    protected DataInputStream in;
    protected SocketFactory socketFactory;
    protected Socket socket;
    private MelSocketListener listener;
    private final int v;
    private static AtomicInteger vv = new AtomicInteger(0);

    public MelAuthService getMelAuthService() {
        return melAuthService;
    }

    public void setRunnableName(String runnableName) {
        this.runnableName = runnableName;
    }

    @Override
    public String getRunnableName() {
        if (this.runnableName != null)
            return runnableName;
        return "MelSocket for " + melAuthService.getName();
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

    static class MelThread extends Thread {

        public MelThread(MelSocket melSocket) {
            super(melSocket);
        }

        @Override
        public void interrupt() {
            super.interrupt();
        }
    }

    public MelSocket allowIsolation() {
        this.allowIsolation = true;
        return this;
    }

    public void start() {
        this.thread = new MelThread(this);
        String name = "THREAD." + (melAuthService == null ? "no service" : melAuthService.getName()) + "." + "id=" + v + "." + getClass().getSimpleName();
        this.thread.setName(name);
        this.thread.start();
    }

    private static String TOO_LONG_APPENDIX = "?";
    private static int MAX_CHARS = 63 * 1024 - 2 - TOO_LONG_APPENDIX.length();

    public void send(String json) {
        try {
            Lok.debug("   " + (melAuthService == null ? "no service" : melAuthService.getName()) + ".MelSocket(v=" + v + ").send to " + socket.getInetAddress().toString() + ": " + json);
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
            Lok.error("MelSocket.send.error: " + e.toString() + " v=" + v);
            e.printStackTrace();
        }
    }

    public interface MelSocketListener {
        void onIsolated();

        void onMessage(MelSocket melSocket, String msg);

        void onOpen();

        void onError(Exception ex);

        void onClose(int code, String reason, boolean remote);

        void onBlockReceived(BlockReceivedJob block);
    }


    public MelSocket(MelAuthService melAuthService, Socket socket) {
        this(melAuthService);
        this.socket = socket;
        streams();
    }

    public MelSocket(MelAuthService melAuthService) {
        this.melAuthService = melAuthService;
        v = vv.getAndIncrement();
        if (melAuthService != null)
            melAuthService.addMelSocket(this);
    }


    private void streams() {
        N.oneLine(() -> {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
        });
    }

    public MelSocket setSocket(Socket socket) {
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
            melAuthService.execute(socketWorker);
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
                        Lok.debug("MelSocket.runImpl");
                    } else {
                        if (msgBuffer.length() > 0) {
                            msgBuffer.append(s);
                            socketWorker.addJob(new ReceivedJob().setMessage(msgBuffer.toString()));
                            msgBuffer = new StringBuffer();
                            continue;
                        }
                    }
                    Lok.debug("   " + melAuthService.getName() + "(v=" + v + ")got(" + socket.getInetAddress() + "): " + s);
                    if (s.equals(MelStrings.msg.MODE_ISOLATE) && allowIsolation) {
                        if (!isIsolated)
                            send(MelStrings.msg.MODE_ISOLATE);
                        else
                            listener.onIsolated();
                        isIsolated = true;
                    } else
                        socketWorker.addJob(new ReceivedJob().setMessage(s));
                }
            }
//            Lok.debug("MelSocket.runTry.CLOSING");
            listener.onClose(42, "don't know shit", true);
        } catch (SSLException e) {
            String line = (melAuthService == null ? "no service" : melAuthService.getName()) + "." + getClass().getSimpleName() + "." + socket.getClass().getSimpleName() + ".runTry.disconnected(interrupted? " + thread.isInterrupted() + ")";
            Lok.error(line);
            Lok.error("SSLException: " + e.toString() + " v=" + v);
        } catch (Exception e) {
            if (!isStopped()) {
                String line = (melAuthService == null ? "no service" : melAuthService.getName()) + "." + getClass().getSimpleName() + "." + socket.getClass().getSimpleName() + ".runTry.disconnected(interrupted? " + thread.isInterrupted() + ")";
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

    public MelSocket setListener(MelSocketListener listener) {
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
