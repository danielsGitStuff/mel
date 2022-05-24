package de.mel.auth.tools.lock2;

public class LockRunnables {
    public interface TransactionRunnable {
        void run() throws Exception;
    }

    public interface TransactionResultRunnable<Ty> {
        Ty run() throws Exception;
    }
}
