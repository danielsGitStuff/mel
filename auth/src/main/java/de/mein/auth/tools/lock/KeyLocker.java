package de.mein.auth.tools.lock;

import de.mein.Lok;
import de.mein.auth.tools.N;

import java.util.*;

@SuppressWarnings("Duplicates")
public class KeyLocker {

    private static String locker = "LOCK!!!";
    private static Map<Object, Key> lockMap = new HashMap<>();
    private static Map<Object, Set<Key>> readMap = new HashMap<>();
    //    private static Map<Object, Key> writeMap = new HashMap<>();
    private static Long LOCK_ID_COUNT = 0L;

    public static LockedTransaction transaction(Object... objects) {
        LockedTransaction transaction = new LockedTransaction();
        try {
            Set<Object> ordinaryObjects = new HashSet<>();
            Set<Object> readObjects = new HashSet<>();
            Set<Key> keyToLockOn = new HashSet<>();
            synchronized (locker) {
                for (Object o : objects) {
                    if (o instanceof Read) {
                        // check if normal lock is held anywhere
                        Read read = (Read) o;
                        for (Object oo : read.getObjects()) {
                            readObjects.add(oo);
                            // check
                            if (lockMap.containsKey(oo)) {
                                Key key = lockMap.get(oo);
                                keyToLockOn.add(key);
//                                key.lock();
                            }
                        }
                    } else {
                        // check if read/normal lock is held anywhere
                        ordinaryObjects.add(o);
                        if (lockMap.containsKey(o)) {
                            // stop on the write/normal key
                            Key key = lockMap.get(o);
                            keyToLockOn.add(key);
//                            key.lock();
                        }
                        if (readMap.containsKey(o)) {
                            // stop on every read key
                            N.forEachIgnorantly(readMap.get(o), Key::lock);
                        }
                    }
                }
            }
            for (Key key : keyToLockOn) {
                key.lock();
                key.unlock();
            }
            Key key = new Key(ordinaryObjects, readObjects);
            transaction.setKey(key);
            key.lock();
            // update currently held locks
            N.forEach(ordinaryObjects, o -> lockMap.put(o, key));
            N.forEach(readObjects, o -> {
                if (!readMap.containsKey(o))
                    readMap.put(o, new HashSet<>());
                readMap.get(o).add(key);
            });
        } catch (Exception e) {
            Lok.error(e.toString());
            end(transaction);
        }
        return transaction;
    }

    static void end(LockedTransaction transaction) {
        Key key = transaction.getKey();
        if (key == null) {
            Lok.error("key was null");
        } else {
            synchronized (locker) {
                N.forEach(key.getObjects(), o -> lockMap.remove(o));
                N.forEach(key.getReadObjects(), o -> {
                    Set<Key> set = readMap.get(o);
                    set.remove(key);
                    if (set.isEmpty())
                        readMap.remove(o);
                });
                key.unlock();
            }
        }
    }


    public static void reset() {
        lockMap.clear();
        readMap.clear();
    }
}
