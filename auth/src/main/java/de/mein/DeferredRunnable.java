package de.mein;

import org.jdeferred.impl.DeferredObject;

/**
 * A Runnable that comes with a Deferred object, that is resolved when the Thread has started
 * Created by xor on 04.09.2016.
 */
public abstract class DeferredRunnable implements MeinRunnable, MeinThread.Interruptable {
    protected DeferredObject<DeferredRunnable, Exception, Void> startedPromise = new DeferredObject<>();
    private Thread thread;

    /**
     * you do not have to override this
     */
    public void shutDown() {
        String line = "shutting down: " + getClass().getSimpleName();
        if (thread != null)
            line += "/" + thread.getName();
        System.out.println(line);
        if (thread != null) {
            thread.interrupt();
        } else {
            System.err.println(getClass().getSimpleName() + ".shutDown: Thread was null :'(  " + getRunnableName());
        }
        onShutDown();
    }

    public abstract void onShutDown();

    @Override
    public void run() {
        thread = Thread.currentThread();
        if (getClass().getSimpleName().equals("MeinAuthService"))
            System.out.println("DeferredRunnable.run");
        runImpl();
    }

    public abstract void runImpl();

    protected boolean isInterrupted() {
        return Thread.currentThread().isInterrupted();
    }

    public DeferredObject<DeferredRunnable, Exception, Void> getStartedDeferred() {
        return startedPromise;
    }
}
