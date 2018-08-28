package de.mein.auth.socket.process.val;

import de.mein.auth.data.IPayload;
import de.mein.auth.tools.CountWaitLock;

/**
 * Created by xor on 23.11.2017.
 */

public class LockedRequest<T extends IPayload> extends Request<T> {
    private CountWaitLock lock = new CountWaitLock();
    private T response;
    private Exception exception;

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    public void setResponse(T response) {
        this.response = response;
    }

    public T getResponse() {
        return response;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public Exception getException() {
        return exception;
    }

    public boolean successful() {
        return response != null && exception == null;
    }
}
