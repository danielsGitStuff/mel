package de.mel.auth.tools.lock2;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class LockObjectEntry implements Comparable<LockObjectEntry> {

    private static AtomicInteger COUNT = new AtomicInteger(1);
    private final Integer id;
    private final Object object;
    private boolean debugLocked;

    private static final Map<Object, LockObjectEntry> INSTANCES = new IdentityHashMap<>();
    private static final String LOCKER = "lock str";

    public static LockObjectEntry create(Object object) {
        synchronized (LOCKER) {
            if (INSTANCES.containsKey(object))
                return INSTANCES.get(object);
            else {
                LockObjectEntry e = new LockObjectEntry(object);
                INSTANCES.put(object, e);
                return e;
            }
        }
    }

    @Override
    public String toString() {
        return "LOE " + id;
    }

    public static void free(LockObjectEntry entry) {
        synchronized (LOCKER) {
            INSTANCES.remove(entry.getObject());
        }
    }

    private LockObjectEntry(Object object) {
        this.object = object;
        this.id = COUNT.getAndIncrement();
    }

    @Override
    public int compareTo(@NotNull LockObjectEntry lockObjectEntry) {
        return this.id.compareTo(lockObjectEntry.id);
    }

    public Object getObject() {
        return object;
    }

    public Integer getId() {
        return id;
    }
}
