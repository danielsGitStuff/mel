package de.mel.auth.socket;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.MelStrings;
import de.mel.auth.jobs.BlockReceivedJob;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.tools.N;
import org.jdeferred.impl.DeferredObject;

import javax.net.SocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Created by xor on 09.08.2016.
 */
public class MelSocket extends DeferredRunnable {


//    public MelSocket setConnectJob(AConnectJob connectJob) {
//        this.connectJob = connectJob;
//        return this;
//    }


    protected volatile boolean allowIsolation = false;
    protected volatile boolean isIsolated = false;
    protected String runnableName = null;

    public MelSocket setIsolated(boolean isolated) {
        isIsolated = isolated;
        return this;
    }

    // 128 kb + meta = 32 * 4kb + meta
//    public static final int BLOCK_SIZE = 32 * 4096 + 21;
    public static final int BLOCK_SIZE = 1024 * 4096 + 21; // performance increase with bigger blocks?

    /**
     * The reader blocks when the worker has this many messages (or, weighted, blocks) to handle.
     */
    private static final int QUEUE_PERMITS = 64;
    private static final int BLOCK_PERMITS = QUEUE_PERMITS / 2;

    protected MelAuthService melAuthService;
    private MelThread thread;
    protected DataOutputStream out;
    protected DataInputStream in;
    protected SocketFactory socketFactory;
    protected Socket socket;
    private volatile MelSocketListener listener;
    private final int v;
    private static AtomicInteger vv = new AtomicInteger(0);

    /**
     * Everything the worker has to do, in the order it happened. Close requests are never held back, only what the reader puts in is limited.
     */
    private final LinkedBlockingQueue<SocketEvent> events = new LinkedBlockingQueue<>();
    private final Semaphore queueSpace = new Semaphore(QUEUE_PERMITS, true);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile CloseReason closeReason;
    private volatile boolean closedByPeer;

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

    /**
     * Starts the worker that handles what is received and the reader that receives it.
     */
    public void start() {
        started.set(true);
        if (closed.get())
            return;
        SocketWorker socketWorker = new SocketWorker(this);
        melAuthService.execute(socketWorker);
        this.thread = new MelThread(this);
        String name = "THREAD." + (melAuthService == null ? "no service" : melAuthService.getName()) + "." + "id=" + v + "." + getClass().getSimpleName();
        this.thread.setName(name);
        this.thread.start();
    }

    private static String TOO_LONG_APPENDIX = "?";
    private static int MAX_CHARS = 63 * 1024 - 2 - TOO_LONG_APPENDIX.length();

    /**
     * Everything that writes to the stream holds this, so frames of different threads do not interleave.
     */
    protected final Object sendLock = new Object();

    public void send(String json) {
        synchronized (sendLock) {
            sendUnlocked(json);
        }
    }

    private void sendUnlocked(String json) {
        try {
            Lok.debug("   " + (melAuthService == null ? "no service" : melAuthService.getName()) + ".MelSocket(v=" + v + ").send to " + socket.getInetAddress().toString() + ": " + json);
            if (socket.isClosed()) {
                Lok.error(getClass().getSimpleName() + ".send(): Socket closed!");
                return;
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

        /**
         * Called once per socket, by its worker, after everything that was received before the end has been handled.
         *
         * @param remote whether the partner ended the connection
         */
        void onClose(CloseReason reason, boolean remote);

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
        close(CloseReason.SHUTDOWN);
        return DeferredRunnable.ResolvedDeferredObject();
    }

    /**
     * Closes the socket after everything that was received before is handled. The listener is told once, by the worker.
     * Nothing is sent or received on a socket that is closing. This is also how a socket that was never started is closed.
     */
    public void close(CloseReason reason) {
        events.add(new SocketEvent.CloseRequested(reason));
        stopped = true;
        if (!started.get())
            finishClose(reason, false);
    }

    /**
     * @return why the socket was closed, null while it is open
     */
    public CloseReason getCloseReason() {
        return closeReason;
    }

    /**
     * @return whether the partner ended the connection. Only valid once {@link #getCloseReason()} is not null.
     */
    public boolean isClosedByPeer() {
        return closedByPeer;
    }

    MelSocketListener getListener() {
        return listener;
    }

    SocketEvent takeEvent() throws InterruptedException {
        return events.take();
    }

    void eventHandled(SocketEvent event) {
        if (event instanceof SocketEvent.MessageReceived)
            queueSpace.release();
        else if (event instanceof SocketEvent.BlockReceived)
            queueSpace.release(BLOCK_PERMITS);
    }

    /**
     * Closes the java socket and tells the listener, exactly once, no matter how often or from where it is called.
     * Called by the worker, or by {@link #close(CloseReason)} when there is no worker.
     */
    void finishClose(CloseReason reason, boolean remote) {
        if (!closed.compareAndSet(false, true))
            return;
        Lok.debug("closing v=" + v + " reason=" + reason + (remote ? " (by the partner)" : ""));
        closeReason = reason;
        closedByPeer = remote;
        stopped = true;
        N.s(() -> in.close());
        N.s(() -> out.close());
        N.s(() -> socket.close());
        try {
            MelSocketListener listener = this.listener;
            if (listener != null)
                listener.onClose(reason, remote);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (melAuthService != null)
                melAuthService.removeMelSocket(this);
        }
    }

    private void enqueue(SocketEvent event, int permits) throws InterruptedException {
        queueSpace.acquire(permits);
        events.add(event);
    }

    /**
     * The reader. It only reads and puts what it reads into the queue of the worker, it never calls anybody.
     */
    @Override
    public void runImpl() {
        Thread thread = Thread.currentThread();
        if (socket == null)
            Lok.error(getClass().getSimpleName() + " running without underlying socket!!!");
        StringBuilder msgBuffer = new StringBuilder();
        try {
            if (in == null || out == null)
                streams();
            while (!isStopped()) {
                if (isIsolated && allowIsolation) {
                    byte[] bytes = new byte[BLOCK_SIZE];
                    in.readFully(bytes);
                    enqueue(new SocketEvent.BlockReceived(bytes), BLOCK_PERMITS);
                } else {
                    String s = in.readUTF();
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
                            enqueue(new SocketEvent.MessageReceived(msgBuffer.toString()), 1);
                            msgBuffer = new StringBuilder();
                            continue;
                        }
                    }
                    Lok.debug("   " + melAuthService.getName() + "(v=" + v + ")got(" + socket.getInetAddress() + "): " + s);
                    if (s.equals(MelStrings.msg.MODE_ISOLATE) && allowIsolation) {
                        if (!isIsolated)
                            send(MelStrings.msg.MODE_ISOLATE);
                        else
                            enqueue(new SocketEvent.IsolateConfirmed(), 1);
                        isIsolated = true;
                    } else
                        enqueue(new SocketEvent.MessageReceived(s), 1);
                }
            }
        } catch (Exception e) {
            if (!isStopped()) {
                String line = (melAuthService == null ? "no service" : melAuthService.getName()) + "." + getClass().getSimpleName() + "." + socket.getClass().getSimpleName() + ".runTry.disconnected(interrupted? " + thread.isInterrupted() + ")";
                Lok.error(line);
                Lok.error(e.getClass().getSimpleName() + ": " + e.toString() + " v=" + v);
            }
            events.add(new SocketEvent.PeerClosed(e));
        }
    }

    public int getV() {
        return v;
    }

    public MelSocket setListener(MelSocketListener listener) {
        this.listener = listener;
        return this;
    }

    /**
     * Same as {@code close(CloseReason.SHUTDOWN)}.
     */
    public void stop() {
        close(CloseReason.SHUTDOWN);
    }
}
