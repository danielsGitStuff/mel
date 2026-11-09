package de.mel.auth.socket;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

/**
 * The queue between the reader of a {@link MelSocket} and its {@link SocketWorker}.
 * Only what the reader puts in is limited (a message costs 1 permit, a block {@link #BLOCK_PERMITS}), so a slow worker
 * stops the reader. Close and error events are never held back. The permits come back when the worker has handled the event.
 */
class SocketEvents {
    private static final int QUEUE_PERMITS = 64;
    private static final int BLOCK_PERMITS = QUEUE_PERMITS / 2;

    private final LinkedBlockingQueue<SocketEvent> events = new LinkedBlockingQueue<>();
    private final Semaphore space = new Semaphore(QUEUE_PERMITS, true);

    /**
     * Called by the reader. Blocks while the worker has too much to handle.
     */
    void putMessage(String message) throws InterruptedException {
        put(new SocketEvent.MessageReceived(message), 1);
    }

    void putBlock(byte[] block) throws InterruptedException {
        put(new SocketEvent.BlockReceived(block), BLOCK_PERMITS);
    }

    /**
     * For close, violation and peer closed: never blocks.
     */
    void putUnlimited(SocketEvent event) {
        events.add(event);
    }

    SocketEvent take() throws InterruptedException {
        return events.take();
    }

    /**
     * Called by the worker when it is done with an event.
     */
    void handled(SocketEvent event) {
        if (event instanceof SocketEvent.MessageReceived)
            space.release();
        else if (event instanceof SocketEvent.BlockReceived)
            space.release(BLOCK_PERMITS);
    }

    private void put(SocketEvent event, int permits) throws InterruptedException {
        space.acquire(permits);
        events.add(event);
    }
}
