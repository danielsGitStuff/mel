package de.mel.auth.tools.lock;

import de.mel.auth.tools.WaitLock;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class Key {
    private final Set<Object> objects;
    private final Set<Object> readObjects;
    private final WLock lock;
    private StackTraceElement[] traceElement;
    // this helps you debugging
    private long id;
    private static AtomicLong ID_COUNTER = new AtomicLong(0L);

    Key(Set<Object> objects) {
        this(objects, new HashSet<>());
    }

    Key(Set<Object> objects, Set<Object> readObjects) {
        this.objects = objects;
        this.readObjects = readObjects;
        this.lock = new WLock();
        this.id = ID_COUNTER.getAndIncrement();
    }

    public Key(Set<Object> ordinaryObjects, Set<Object> readObjects, StackTraceElement[] traceElement) {
        this(ordinaryObjects,readObjects);
        this.traceElement = traceElement;
    }

    @Override
    public String toString() {
        return "Key " + id;
    }

    Set<Object> getObjects() {
        return objects;
    }

    Set<Object> getReadObjects() {
        return readObjects;
    }

    void lock() {
        try {
            this.lock.lock();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
