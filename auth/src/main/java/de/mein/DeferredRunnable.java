package de.mein;

import org.jdeferred.impl.DeferredObject;

/**
 * A Runnable that comes with a Deferred object, that is resolved when the Thread has started
 * Created by xor on 04.09.2016.
 */
public abstract class DeferredRunnable implements MeinRunnable, MeinThread.Interruptable {
    protected DeferredObject<DeferredRunnable, Exception, Void> startedPromise = new DeferredObject<>();
    private Thread thread;

    public void shutDown() {
        if (thread != null) {
            thread.interrupt();
        } else {
            System.err.println("DeferredRunnable.shutDown: Thread was null :'(");
        }
    }

    @Override
    public void run() {
        thread = Thread.currentThread();
        runImpl();
    }

    public abstract void runImpl();

    public DeferredObject<DeferredRunnable, Exception, Void> getStartedDeferred() {
        return startedPromise;
    }
}
