package de.mel.auth.socket;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.jobs.BlockReceivedJob;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.tools.N;
import fun.with.promise.Promise;
import fun.with.promise.PromiseImpl;

import javax.net.SocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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


    protected String runnableName = null;

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

    /**
     * Everything that writes to the stream holds this, so frames of different threads do not interleave.
     */
    protected final Object sendLock = new Object();

    /**
     * Sends a message frame (UTF-8). A message that does not fit into a frame is not sent at all.
     */
    public void send(String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > FrameCodec.MAX_FRAME) {
            Lok.error(getClass().getSimpleName() + ".send(): message of " + bytes.length + " bytes exceeds the maximum frame size, NOT SENT: " + json.substring(0, Math.min(200, json.length())));
            return;
        }
        Lok.debug("   " + (melAuthService == null ? "no service" : melAuthService.getName()) + ".MelSocket(v=" + v + ").send to " + (socket == null ? "?" : socket.getInetAddress()) + ": " + json);
        write(FrameCodec.MESSAGE, bytes);
    }

    /**
     * Sends a block frame. Must not be longer than {@link FrameCodec#MAX_FRAME}.
     */
    public void sendBlock(byte[] block) {
        write(FrameCodec.BLOCK, block);
    }

    private void write(byte type, byte[] payload) {
        synchronized (sendLock) {
            try {
                if (socket.isClosed()) {
                    Lok.error(getClass().getSimpleName() + ".send(): Socket closed!");
                    return;
                }
                FrameCodec.write(out, type, payload);
            } catch (IOException e) {
                Lok.error("MelSocket.send.error: " + e.toString() + " v=" + v);
            }
        }
    }

    public interface MelSocketListener {
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
    public Promise<Void, Void> onShutDown() {
        close(CloseReason.SHUTDOWN);
        return DeferredRunnable.ResolvedDeferredObject();
    }

    /**
     * Closes the socket after everything that was received before is handled. The listener is told once, by the worker.
     * Nothing is sent or received on a socket that is closing. This is also how a socket that was never started is closed.
     */
    public void close(CloseReason reason) {
        close(reason, false);
    }

    /**
     * @param remote whether the partner is the one who ended the connection (it told us so)
     */
    public void close(CloseReason reason, boolean remote) {
        events.add(new SocketEvent.CloseRequested(reason, remote));
        stopped = true;
        if (!started.get())
            finishClose(reason, remote);
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
        try {
            if (in == null || out == null)
                streams();
            while (!isStopped()) {
                FrameCodec.Frame frame = FrameCodec.read(in);
                if (frame.type == FrameCodec.BLOCK) {
                    enqueue(new SocketEvent.BlockReceived(frame.payload), BLOCK_PERMITS);
                } else {
                    String s = new String(frame.payload, StandardCharsets.UTF_8);
                    Lok.debug("   " + melAuthService.getName() + "(v=" + v + ")got(" + socket.getInetAddress() + "): " + s);
                    enqueue(new SocketEvent.MessageReceived(s), 1);
                }
            }
        } catch (FrameCodec.ProtocolViolationException e) {
            Lok.error(getClass().getSimpleName() + " v=" + v + " protocol violation: " + e.getMessage());
            events.add(new SocketEvent.Violation(e.getMessage()));
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
