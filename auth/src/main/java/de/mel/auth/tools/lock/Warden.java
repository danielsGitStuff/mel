package de.mel.auth.tools.lock;

import de.mel.Lok;
import de.mel.auth.tools.N;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds read locks and normal locks on Objects that you put in T.lockingTransaction().
 */
@SuppressWarnings("Duplicates")
public class Warden<Type> {

    private Key key;
    private boolean finished = false;
    private boolean permaLocked = false;
    private List<TransactionRunnable> after = new ArrayList<>();

    Warden(boolean permaLocked) {
        this.permaLocked = permaLocked;
    }

    void setKey(Key key) {
        this.key = key;
    }

    /**
     * Ends the transaction. It calls all {@link TransactionRunnable}s and stops holding any locks afterwards.
     */
    public synchronized Warden<Type> end() {
        if (!finished) {
            finished = true;
            N.forEachIgnorantly(after, TransactionRunnable::run);
            P.free(this);
        }
        return this;
    }

    Key getKey() {
        return key;
    }

    private Object result;

    public Object getResult() {
        return result;
    }

    public <X, Y> Warden<Y> runResult(TransactionResultRunnableExtended<Type, Y> resultRunnable) {
        if (finished) {
            Lok.error("lockingTransaction already finished!");
            return null;
        }
        if (!permaLocked)
            P.access(this);
        try {
            result = resultRunnable.run((Type) result);
        } catch (Exception e) {
            Lok.error("lockingTransaction failed: " + e.toString() + " msg: " + e.getMessage());
            end();
        } finally {
            if (!permaLocked)
                P.release(this);
        }
        return (Warden<Y>) this;
    }

    /**
     * executes some code and has a return value.
     *
     * @param resultRunnable
     * @param <Y>
     * @return
     */
    public <Y> Warden<Y> runResult(TransactionResultRunnable<Y> resultRunnable) {
        if (finished) {
            Lok.error("lockingTransaction already finished!");
            return null;
        }
        if (!permaLocked)
            P.access(this);
        try {
            result = resultRunnable.run();
        } catch (Exception e) {
            Lok.error("lockingTransaction failed: " + e.toString() + " msg: " + e.getMessage());
            end();
        } finally {
            if (!permaLocked)
                P.release(this);
        }
        return (Warden<Y>) this;
    }

    /**
     * Executes some code within this transaction.
     *
     * @param runnable
     * @return
     */
    public Warden run(TransactionRunnable runnable) {
        if (finished) {
            Lok.error("lockingTransaction already finished!");
            return this;
        }
        if (!permaLocked)
            P.access(this);
        try {
            runnable.run();
        } catch (Exception e) {
            Lok.error("lockingTransaction failed: " + e.toString() + " msg: " + e.getMessage());
            e.printStackTrace();
            end();
        } finally {
            if (!permaLocked)
                P.release(this);
        }
        return this;
    }

    public Type get() {
        return (Type) result;
    }

    /**
     * execute this when end() is called.
     *
     * @param transactionRunnable
     */
    public void after(TransactionRunnable transactionRunnable) {
        this.after.add(transactionRunnable);
    }

    public interface TransactionRunnable {
        void run() throws Exception;
    }

    public interface TransactionResultRunnableExtended<X, Y> {
        Y run(X x) throws Exception;
    }

    public interface TransactionResultRunnable<Ty> {
        Ty run() throws Exception;
    }
}
