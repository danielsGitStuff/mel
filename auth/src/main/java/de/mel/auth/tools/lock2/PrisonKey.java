package de.mel.auth.tools.lock2;

import de.mel.Lok;
import de.mel.auth.tools.N;
import de.mel.auth.tools.lock.Warden;
import de.mel.sql.RWLock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;

public class PrisonKey {
    //    private final Set<Object> readObjects, writeObjects;
    private final Map<Object, RWLock> readObjectMap, writeObjectMap;
    private final StackTraceElement[] traceElement;

    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);

    private final Semaphore semaphore = new Semaphore(1, false);
    private final int id;

    private boolean finished = false;
    private List<Warden.TransactionRunnable> after = new ArrayList<>();


    public PrisonKey(Map<Object, RWLock> readObjectMap, Map<Object, RWLock> writeObjectMap, StackTraceElement[] traceElement) {
        this.readObjectMap = readObjectMap;
        this.writeObjectMap = writeObjectMap;
        this.traceElement = traceElement;
        this.id = PrisonKey.ID_COUNTER.getAndIncrement();
    }

    public StackTraceElement[] getTraceElement() {
        return traceElement;
    }

    @Override
    public String toString() {
        return "Key " + traceElement[traceElement.length - 1];
    }

    public Map<Object, RWLock> getWriteObjectMap() {
        return writeObjectMap;
    }

    void setFinished(boolean finished) {
        this.finished = finished;
    }

    public boolean isFinished() {
        return finished;
    }

    public Map<Object, RWLock> getReadObjectMap() {
        return readObjectMap;
    }

    void lockImpl() {
        for (Map.Entry<Object, RWLock> entry : readObjectMap.entrySet()) {
            Lok.debug("read lock on " + entry.getClass().getSimpleName());
            entry.getValue().lockRead();
        }
        for (Map.Entry<Object, RWLock> entry : writeObjectMap.entrySet()) {
            Lok.debug("write lock on " + entry.getClass().getSimpleName());
            entry.getValue().lockWrite();
        }
    }

    void unlockImpl() {
        for (Map.Entry<Object, RWLock> entry : writeObjectMap.entrySet()) {
            Lok.debug("write unlock on " + entry.getClass().getSimpleName());
            entry.getValue().unlockWrite();
        }
        for (Map.Entry<Object, RWLock> entry : readObjectMap.entrySet()) {
            Lok.debug("read unlock on " + entry.getClass().getSimpleName());
            entry.getValue().unlockRead();
        }

    }

    public PrisonKey lock() {
        Prison.access(this);
        return this;
    }

    public PrisonKey unlock() {
        Prison.release(this);
        return this;
    }

    int getId() {
        return id;
    }


    public void release() {
        Prison.release(this);
    }

    public PrisonKey end() {
        Lok.debug("ending prison key");
        synchronized (this) {
            if (finished) {
                Lok.error("PrisonKey has already ended!");
                return null;
            }
            this.finished = true;
            N.forEachIgnorantly(after, Warden.TransactionRunnable::run);
            release();
        }
        return this;
    }

    public PrisonKey run(Warden.TransactionRunnable runnable) {
        Prison.access(this);
        synchronized (this) {
            if (finished) {
                Lok.error("PrisonKey was already finished");
                return null;
            }
            try {
                runnable.run();
            } catch (Exception e) {
                Lok.error("lockingTransaction failed: " + e + " msg: " + e.getMessage());
                end();
            }
        }
        return this;
    }

    /**
     * execute this when end() is called.
     *
     * @param transactionRunnable
     */
    public void after(Warden.TransactionRunnable transactionRunnable) {
        this.after.add(transactionRunnable);
    }
}
