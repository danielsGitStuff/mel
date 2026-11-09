package de.mel.auth.socket;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.jobs.BlockReceivedJob;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.tools.N;
import fun.with.promise.Promise;

import javax.net.SocketFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Created by xor on 09.08.2016.
 */
public class MelSocket extends DeferredRunnable {


    protected String runnableName = null;

    protected MelAuthService melAuthService;
    protected DataOutputStream out;
    protected DataInputStream in;
    protected SocketFactory socketFactory;
    protected Socket socket;
    private volatile MelSocketListener listener;
    /**
     * Whoever uses this socket for something bigger, null for a plain socket.
     */
    private volatile Object owner;
    private final int v;
    private static AtomicInteger vv = new AtomicInteger(0);

    /**
     * Everything the worker has to do, in the order it happened.
     */
    private final SocketEvents events = new SocketEvents();
    private final AtomicBoolean started = new AtomicBoolean(false);
    /**
     * null while open, set once by whoever closes first
     */
    private final AtomicReference<SocketState.Closed> ended = new AtomicReference<>();

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

    /**
     * Starts the worker that handles what is received and the reader that receives it.
     */
    public void start() {
        started.set(true);
        if (ended.get() != null)
            return;
        SocketWorker socketWorker = new SocketWorker(this);
        melAuthService.execute(socketWorker);
        Thread reader = new Thread(this, "THREAD." + serviceName() + ".id=" + v + "." + getClass().getSimpleName());
        reader.start();
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
        Lok.debug("   " + serviceName() + ".MelSocket(v=" + v + ").send to " + (socket == null ? "?" : socket.getInetAddress()) + ": " + json);
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


    private String serviceName() {
        return melAuthService == null ? "no service" : melAuthService.getName();
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
        events.putUnlimited(new SocketEvent.CloseRequested(reason, remote));
        stopped = true;
        if (!started.get())
            finishClose(reason, remote);
    }

    /**
     * @return why the socket was closed, null while it is open
     */
    public CloseReason getCloseReason() {
        SocketState.Closed closed = ended.get();
        return closed == null ? null : closed.reason();
    }

    /**
     * @return whether the partner ended the connection. Only valid once {@link #getCloseReason()} is not null.
     */
    public boolean isClosedByPeer() {
        SocketState.Closed closed = ended.get();
        return closed != null && closed.remote();
    }

    MelSocketListener getListener() {
        return listener;
    }

    SocketEvents getEvents() {
        return events;
    }

    /**
     * Closes the java socket and tells the listener, exactly once, no matter how often or from where it is called.
     * Called by the worker, or by {@link #close(CloseReason)} when there is no worker.
     */
    void finishClose(CloseReason reason, boolean remote) {
        if (!ended.compareAndSet(null, new SocketState.Closed(reason, remote)))
            return;
        Lok.debug("closing v=" + v + " reason=" + reason + (remote ? " (by the partner)" : ""));
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
                    events.putBlock(frame.payload);
                } else {
                    String s = new String(frame.payload, StandardCharsets.UTF_8);
                    Lok.debug("   " + serviceName() + "(v=" + v + ")got(" + socket.getInetAddress() + "): " + s);
                    events.putMessage(s);
                }
            }
        } catch (FrameCodec.ProtocolViolationException e) {
            Lok.error(getClass().getSimpleName() + " v=" + v + " protocol violation: " + e.getMessage());
            events.putUnlimited(new SocketEvent.Violation(e.getMessage()));
        } catch (Exception e) {
            if (!isStopped()) {
                String line = serviceName() + "." + getClass().getSimpleName() + "." + socket.getClass().getSimpleName() + ".runTry.disconnected(interrupted? " + thread.isInterrupted() + ")";
                Lok.error(line);
                Lok.error(e.getClass().getSimpleName() + ": " + e.toString() + " v=" + v);
            }
            events.putUnlimited(new SocketEvent.PeerClosed(e));
        }
    }

    public Object getOwner() {
        return owner;
    }

    void setOwner(Object owner) {
        this.owner = owner;
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
