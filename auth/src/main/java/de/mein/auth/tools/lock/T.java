package de.mein.auth.tools.lock;

import de.mein.Lok;
import de.mein.auth.tools.N;

import java.util.*;

@SuppressWarnings("Duplicates")
public class T {

    private static String locker = "LOCK!!!";
    private static Map<Object, Key> lockMap = new HashMap<>();
    private static Map<Object, Set<Key>> readMap = new HashMap<>();
    //    private static Map<Object, Key> writeMap = new HashMap<>();
    private static Long LOCK_ID_COUNT = 0L;

    /**
     * Objects put in here are read locked.
     *
     * @param objects
     * @return
     */
    public static Read read(Object... objects) {
        return new Read(objects);
    }

    /**
     * You can lock on all Objects that you put in here. If you want some objects read locked only call T.read(yourObjectHere).
     *
     * @param objects
     * @return
     */
    public static Transaction lockingTransaction(Object... objects) {
        Transaction transaction = new Transaction(true);
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


    /**
     * You can lock on all Objects that you put in here. If you want some objects read locked only call T.read(yourObjectHere).
     *
     * @param objects
     * @return
     */
    public static Transaction tNoLock(Object... objects) {
        Transaction transaction = new Transaction(false);
        try {
            Set<Object> ordinaryObjects = new HashSet<>();
            Set<Object> readObjects = new HashSet<>();
            synchronized (locker) {
                for (Object o : objects) {
                    if (o instanceof Read) {
                        // check if normal lock is held anywhere
                        Read read = (Read) o;
                        for (Object oo : read.getObjects()) {
                            readObjects.add(oo);
                        }
                    } else {
                        // check if read/normal lock is held anywhere
                        ordinaryObjects.add(o);
                    }
                }
            }
            Key key = new Key(ordinaryObjects, readObjects);
            transaction.setKey(key);
//            key.lock();
            // update currently held locks
//            N.forEach(ordinaryObjects, o -> lockMap.put(o, key));
//            N.forEach(readObjects, o -> {
//                if (!readMap.containsKey(o))
//                    readMap.put(o, new HashSet<>());
//                readMap.get(o).add(key);
//            });
        } catch (Exception e) {
            Lok.error(e.toString());
            end(transaction);
        }
        return transaction;
    }

    static void end(Transaction transaction) {
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

    public static void release(Transaction transaction) {
        synchronized (locker) {
            Key key = transaction.getKey();
            for (Object o : key.getObjects())
                lockMap.remove(o);
            for (Object o : key.getReadObjects()) {
                Set<Key> set = readMap.get(o);
                readMap.remove(key);
                if (set.isEmpty())
                    readMap.remove(o);
            }
            key.unlock();
        }
    }

    public static void access(Transaction transaction) {
        try {
            Set<Object> ordinaryObjects = new HashSet<>();
            Set<Object> readObjects = new HashSet<>();
            Set<Key> keyToLockOn = new HashSet<>();
            synchronized (locker) {
                for (Object o : transaction.getKey().getObjects()) {
                    if (lockMap.containsKey(o)) {
                        keyToLockOn.add(lockMap.get(o));
                    } else if (readMap.containsKey(o))
                        keyToLockOn.addAll(readMap.get(o));
                }
            }
            for (Key key : keyToLockOn) {
                if (key != transaction.getKey()) {
                    key.lock();
                    key.unlock();
                }
            }
            Key key = transaction.getKey();
            key.lock();
            // update currently held locks
            synchronized (locker) {
                N.forEach(key.getObjects(), o -> lockMap.put(o, key));
                N.forEach(key.getReadObjects(), o -> {
                    if (!readMap.containsKey(o))
                        readMap.put(o, new HashSet<>());
                    readMap.get(o).add(key);
                });
            }
        } catch (Exception e) {
            Lok.error(e.toString());
            end(transaction);
        }
    }
}
