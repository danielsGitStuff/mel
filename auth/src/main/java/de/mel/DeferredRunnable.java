package de.mel;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

/**
 * A Runnable that comes with a Deferred object, that is resolved when the Thread has started.
 * Extend this class, grab the startedPromise in a place that wants to know when this thing has started.
 * Then execute it and resolve the startedPromise somewhere in the run() method.
 * Created by xor on 04.09.2016.
 */
public abstract class DeferredRunnable implements MelRunnable {
    protected DeferredObject<DeferredRunnable, Exception, Void> startedPromise = new DeferredObject<>();
    protected Thread thread;
    protected boolean stopped = false;

    public static DeferredObject<Void, Void, Void> ResolvedDeferredObject(){
      DeferredObject<Void,Void,Void> deferredObject = new DeferredObject<>();
      deferredObject.resolve(null);
      return deferredObject;
    }

    /**
     * you must not override this
     *
     * @return
     */
    public Promise<Void, Void, Void> shutDown() {
        stopped = true;
        return onShutDown();
    }

    /**
     * Is called after shutDown() was called. The current Thread is already stopped.
     * You may want to shut down other components as well. They should be stopped but might block somewhere.
     * Unblock them here.
     */
    public abstract Promise<Void, Void, Void> onShutDown();

    @Override
    public void run() {
        stopped = false;
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

    public DeferredObject<DeferredRunnable, Exception, Void> getStartedDeferred() {
        return startedPromise;
    }

    public void stop() {
        stopped = true;
//        if (thread != null)
//            thread.interrupt();
    }

    public DeferredRunnable setStartedPromise(DeferredObject<DeferredRunnable, Exception, Void> startedPromise) {
        this.startedPromise = startedPromise;
        return this;
    }
}
