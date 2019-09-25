package de.mel.auth.tools;

/**
 * shall replace {@link WaitLock}
 * Created by xor on 23.11.2017.
 */

public class CountWaitLock extends CountLock {

    public CountWaitLock() {
        super();
        lock();
    }
}
