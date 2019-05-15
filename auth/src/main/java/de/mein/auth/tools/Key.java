package de.mein.auth.tools;

import java.util.HashSet;
import java.util.Set;

public class Key {
    private final Set<Object> objects;
    private final WaitLock lock;

    Key(Object[] objects) {
        this.objects = new HashSet<>();
        N.forEach(objects, this.objects::add);
        this.lock = new WaitLock();
    }

    Set<Object> getLockObjects() {
        return objects;
    }

    void lock() {
        this.lock.lock();
    }

    void unlock() {
        this.lock.unlock();
    }
}
