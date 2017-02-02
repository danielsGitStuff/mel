package de.mein;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

/**
 * Created by xor on 04.09.2016.
 */
public abstract class MeinRunnable implements Runnable {
    protected DeferredObject<MeinRunnable, Exception, Void> startedPromise = new DeferredObject<>();
    protected MeinThread thread;

    public Promise<MeinRunnable, Exception, Void> getStartedPromise() {
        return startedPromise;
    }

    public DeferredObject<MeinRunnable, Exception, Void> start(){
        thread = new MeinThread(this);
        thread.setName(getClass().getSimpleName());
        thread.start();
        return startedPromise;
    }

}
