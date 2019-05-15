package de.mein.auth.tools.lock;

import de.mein.Lok;

/**
 * Holds read locks and normal locks on Objects that you put in T.transaction().
 */
public class Transaction {

    private Key key;
    private boolean finished = false;

    void setKey(Key key) {
        this.key = key;
    }

    public synchronized void end() {
        if (!finished) {
            finished = true;
            T.end(this);
        }
    }

    Key getKey() {
        return key;
    }

    public <Ty> Ty runResult(TransactionResultRunnable<Ty> resultRunnable) {
        if (finished) {
            Lok.error("transaction already finished!");
            return null;
        }
        Ty result = null;
        try {
            result = resultRunnable.run();
        } catch (Exception e) {
            Lok.error("transaction failed: " + e.toString() + " msg: " + e.getMessage());
            end();
        }
        return result;
    }

    public Transaction run(TransactionRunnable runnable) {
        if (finished) {
            Lok.error("transaction already finished!");
            return this;
        }
        try {
            runnable.run();
        } catch (Exception e) {
            Lok.error("transaction failed: " + e.toString() + " msg: " + e.getMessage());
            end();
        }
        return this;
    }

    public interface TransactionRunnable {
        void run() throws Exception;
    }


    public interface TransactionResultRunnable<Ty> {
        Ty run() throws Exception;
    }
}
