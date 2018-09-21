package de.mein;

import de.mein.auth.tools.CountdownLock;

/**
 * Created by xor on 21.08.2017.
 */
public abstract class WaitingDeferredRunnable extends DeferredRunnable {
    private CountdownLock countdownLock = new CountdownLock(1);

    @Override
    public void run() {
        thread = Thread.currentThread();
        thread.setName(getRunnableName());
        countdownLock.lock();
        runImpl();
        Lok.debug(getClass().getSimpleName() + ".run.done on " + thread.getName());
    }

    public void begin() {
        countdownLock.unlock();
    }
}
