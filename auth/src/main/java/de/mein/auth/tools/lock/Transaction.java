package de.mein.auth.tools.lock;

import de.mein.Lok;

public class Transaction {

    private Key key;
    private boolean finished = false;

    void setKey(Key key) {
        this.key = key;
    }

    public synchronized void end() {
        finished = true;
        Locker.end(this);
    }

    Key getKey() {
        return key;
    }

    public Transaction run(Runnable runnable) {
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
}
