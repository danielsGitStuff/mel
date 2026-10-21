package de.mel;

import fun.with.promise.Promise;
import fun.with.promise.PromiseImpl;

/**
 * A Runnable that comes with a Deferred object, that is resolved when the Thread has started.
 * Extend this class, grab the startedPromise in a place that wants to know when this thing has started.
 * Then execute it and resolve the startedPromise somewhere in the run() method.
 * Created by xor on 04.09.2016.
 */
public abstract class DeferredRunnable implements MelRunnable {
    protected PromiseImpl<DeferredRunnable, Exception> startedPromise = PromiseImpl.withoutThrowingOnException();
    protected Thread thread;
    protected volatile boolean stopped = false;

    public static Promise<Void, Void> ResolvedDeferredObject() {
        return Promise.resolvedOk();
    }

    /**
     * you must not override this
     *
     * @return
     */
    public Promise<Void, Void> shutDown() {
        stopped = true;
        return onShutDown();
    }

    /**
     * Is called after shutDown() was called. The current Thread is already stopped.
     * You may want to shut down other components as well. They should be stopped but might block somewhere.
     * Unblock them here.
     */
    public abstract Promise<Void, Void> onShutDown();

    @Override
    public void run() {
        // a stop() that came before the thread started must not be lost, see restart()
        if (stopped)
            return;
        thread = Thread.currentThread();
        thread.setName(getRunnableName());
        runImpl();
//        Lok.debug(getClass().getSimpleName() + ".run.done on " + thread.getName());
    }

    /**
     * This is where the stuff you would usually do in run() belongs.
     */
    public abstract void runImpl();

    /**
     * call this from(!) the running Thread to see whether or not the Thread should stop.
     *
     * @return
     */
    public boolean isStopped() {
        return stopped;
    }

    /**
     * Says that this runnable is running. Settles the started promise once: whatever comes after the first outcome is ignored,
     * a start that is reported twice (e.g. by a catch block and the code after it) must not throw.
     *
     * @return whether this call settled it
     */
    protected boolean resolveStarted() {
        synchronized (this) {
            if (!startedPromise.isPending())
                return false;
            startedPromise.resolveOk(this);
            return true;
        }
    }

    /**
     * Says that this runnable could not start. Settles the started promise once, see {@link #resolveStarted()}.
     *
     * @return whether this call settled it
     */
    protected boolean rejectStarted(Exception reason) {
        synchronized (this) {
            if (!startedPromise.isPending())
                return false;
            startedPromise.resolveError(reason);
            return true;
        }
    }

    public PromiseImpl<DeferredRunnable, Exception> getStartedDeferred() {
        return startedPromise;
    }

    /**
     * Makes a stopped runnable runnable again. Call this before executing it a second time.
     * run() does not do this by itself, otherwise a stop() that comes before the thread has started would be lost.
     */
    public void restart() {
        stopped = false;
    }

    public void stop() {
        stopped = true;
//        if (thread != null)
//            thread.interrupt();
    }

    public DeferredRunnable setStartedPromise(PromiseImpl<DeferredRunnable, Exception> startedPromise) {
        this.startedPromise = startedPromise;
        return this;
    }
}
