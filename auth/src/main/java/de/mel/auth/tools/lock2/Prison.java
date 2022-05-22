package de.mel.auth.tools.lock2;

import de.mel.Lok;
import de.mel.sql.RWLock;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Prison {

    private static final String LOCK_STRING = "lock string";

    private static final Map<Object, RWLock> objectLockMap = new HashMap<>();
    private static final Set<PrisonKey> existingKeys = new HashSet<>();
    private static final Map<Object, Set<PrisonKey>> activeReadObjectKeyMap = new HashMap<>();
    private static final Map<Object, Set<PrisonKey>> activeWriteObjectKeyMap = new HashMap<>();
    private static final Set<PrisonKey> activePrisonKeys = new HashSet<>();
//    private static final Map<Object, ReadWriteLock> writeMap = new HashMap<>();
//    private static final Map<Object, Set<ReadWriteLock>> readMap = new HashMap<>();
//
//    private static final Set<PrisonKey> activeKeys = new HashSet<>();


    public static Read read(Object... objects) {
        return new Read(objects);
    }

    public static PrisonKey confine(Object... objects) {
        PrisonKey prisonKey;
        synchronized (LOCK_STRING) {
            Set<Object> readSet = new HashSet<>();
            Set<Object> writeSet = new HashSet<>();
            for (Object o : objects) {
                if (o instanceof Read) {
                    readSet.addAll(Arrays.asList(((Read) o).getObjects()));
                } else {
                    writeSet.add(o);
                }
            }
            StackTraceElement[] traceElement = Thread.currentThread().getStackTrace();
            Map<Object, RWLock> readObjectMap = new HashMap<>();
            Map<Object, RWLock> writeObjectMap = new HashMap<>();
            for (Object o : readSet) {
                RWLock lock = objectLockMap.getOrDefault(o, new RWLock());
                objectLockMap.put(o, lock);
                readObjectMap.put(o, lock);
            }
            for (Object o : writeSet) {
                RWLock lock = objectLockMap.getOrDefault(o, new RWLock());
                objectLockMap.put(o, lock);
                writeObjectMap.put(o, lock);
            }
            prisonKey = new PrisonKey(readObjectMap, writeObjectMap, traceElement);
            existingKeys.add(prisonKey);
        }
        return prisonKey;
    }

    public static void access(PrisonKey prisonKey) {
        synchronized (Prison.LOCK_STRING) {
            for (Object o : prisonKey.getReadObjectMap().keySet()) {
                activeReadObjectKeyMap.getOrDefault(o, new HashSet<>()).add(prisonKey);
            }
            for (Object o : prisonKey.getWriteObjectMap().keySet()) {
                activeWriteObjectKeyMap.getOrDefault(o, new HashSet<>()).add(prisonKey);
            }
            Prison.activePrisonKeys.add(prisonKey);
        }
        prisonKey.lockImpl();
    }

//    public static void unaccess(PrisonKey prisonKey) {
//        synchronized (Prison.LOCK_STRING) {
//            prisonKey.unlock();
//            for (Object o : prisonKey.getReadObjectMap().keySet()) {
//                activeReadObjectKeyMap.getOrDefault(o, new HashSet<>()).remove(prisonKey);
//            }
//            for (Object o : prisonKey.getWriteObjectMap().keySet()) {
//                activeWriteObjectKeyMap.getOrDefault(o, new HashSet<>()).remove(prisonKey);
//            }
//            Prison.activePrisonKeys.remove(prisonKey);
//        }
//    }

    public static void release(PrisonKey prisonKey) {
        synchronized (Prison.LOCK_STRING) {
            prisonKey.unlockImpl();
            for (Object o : prisonKey.getReadObjectMap().keySet()) {
                activeReadObjectKeyMap.getOrDefault(o, new HashSet<>()).remove(prisonKey);
            }
            for (Object o : prisonKey.getWriteObjectMap().keySet()) {
                activeWriteObjectKeyMap.getOrDefault(o, new HashSet<>()).remove(prisonKey);
            }
            Prison.activePrisonKeys.remove(prisonKey);
            Prison.existingKeys.remove(prisonKey);
        }
    }

    public static void debugPrint() {
        Lok.debug("write keys");
//        Set<PrisonKey> keys = new HashSet<>();
//        N.forEach(writeMap.values(), keys::add);
//        N.forEach(readMap.values(), keys::addAll);
//        for (PrisonKey key : keys) {
//            Lok.debug(key.getId() + " f: " + key.isFinished() + " t: " + key.getTraceElement()[key.getTraceElement().length - 1]);
//        }
    }
}
