package de.mein.auth.tools.lock;

import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;

import java.util.HashSet;
import java.util.Set;

public class Key {
    private final Set<Object> objects;
    private final Set<Object> readObjects;
    private final WaitLock lock;
    // this helps you debugging
    private Long id;

    Key(Set<Object> objects) {
        this(objects, new HashSet<>());
    }

    Key(Set<Object> objects, Set<Object> readObjects) {
        this.objects = objects;
        this.readObjects = readObjects;
        this.lock = new WaitLock();
    }

    Set<Object> getObjects() {
        return objects;
    }

    Set<Object> getReadObjects() {
        return readObjects;
    }

    void lock() {
        this.lock.lock();
    }

    void unlock() {
        this.lock.unlock();
    }

    void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
