package de.mel.auth.tools.lock;

import de.mel.Lok;
import de.mel.auth.tools.N;

import java.util.*;

@SuppressWarnings("Duplicates")
/**
 *      Central point that controls all living {@link Warden} objects.
 *      Think of P stands for a prison where you lock up mischievous objects!
 */
public class P {

    private static String locker = "LOCK!!!";
    private static Map<Object, Key> lockMap = new HashMap<>();
    private static Map<Object, Set<Key>> readMap = new HashMap<>();
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
     * Runs a {@link Warden.TransactionRunnable} on objects and calls end() finally.
     * Useful when the current thread might die during waiting for or even running the transaction.
     * @param runnable
     * @param objects
     */
    public static void lockingRun(Warden.TransactionRunnable runnable, Object... objects) {
        Warden warden = null;
        try {
            warden = P.confine(objects);
        } finally {
            warden.run(runnable);
            if (warden != null)
                warden.end();
        }
    }

    /**
     * You can lock on all Objects that you put in here. If you want some objects read locked only call T.read(yourObjectHere).
     *
     * @param objects
     * @return
     */
    public static Warden confine(Object... objects) {
        Warden warden = new Warden(true);
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
                            }
                        }
                    } else {
                        // check if read/normal lock is held anywhere
                        ordinaryObjects.add(o);
                        if (lockMap.containsKey(o)) {
                            // stop on the write/normal key
                            Key key = lockMap.get(o);
                            keyToLockOn.add(key);
                        }
                        if (readMap.containsKey(o)) {
                            // stop on every read key
                            N.forEachIgnorantly(readMap.get(o), keyToLockOn::add);
                        }
                    }
                }
            }
            for (Key key : keyToLockOn) {
                key.lock();
                key.unlock();
            }
            StackTraceElement[] traceElement = Thread.currentThread().getStackTrace();
            Key key = new Key(ordinaryObjects, readObjects, traceElement);
            warden.setKey(key);
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
            free(warden);
        }
        return warden;
    }


    /**
     * You can lock on all Objects that you put in here. If you want some objects read locked only call T.read(yourObjectHere).
     * This method does not lock the objects until you call run().
     *
     * @param objects
     * @return
     */
    public static Warden onProbation(Object... objects) {
        Warden warden = new Warden(false);
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
            warden.setKey(key);
        } catch (Exception e) {
            Lok.error(e.toString());
            free(warden);
        }
        return warden;
    }

    static void free(Warden warden) {
        Key key = warden.getKey();
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

    static void release(Warden warden) {
        synchronized (locker) {
            Key key = warden.getKey();
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

    static void access(Warden warden) {
        try {
            Set<Key> keyToLockOn = new HashSet<>();
            synchronized (locker) {
                for (Object o : warden.getKey().getObjects()) {
                    if (lockMap.containsKey(o)) {
                        keyToLockOn.add(lockMap.get(o));
                    } else if (readMap.containsKey(o))
                        keyToLockOn.addAll(readMap.get(o));
                }
            }
            for (Key key : keyToLockOn) {
                if (key != warden.getKey()) {
                    key.lock();
                    key.unlock();
                }
            }
            Key key = warden.getKey();
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
            free(warden);
        }
    }
}
