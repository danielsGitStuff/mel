package de.mein.auth.tools;

import java.util.HashMap;
import java.util.Map;

public class KeyLocker {


    private static Map<Object, Key> lockMap = new HashMap<>();

    public static synchronized Key lockOn(Object... objects) {
        Object firstObjectInMap = N.first(objects, o -> lockMap.containsKey(o));

        if (firstObjectInMap == null) {
            Key key = new Key(objects);
            N.forEach(objects, o -> lockMap.put(o, key));
            key.lock();
            return key;
        } else {
            Key key = lockMap.get(firstObjectInMap);
            key.lock();
            return key;
        }
    }

    public static synchronized void access(Key key, Object... objects) {
        boolean keyContainsAllObjects = N.all(objects, o -> key.getLockObjects().contains(o));
        boolean keyMatchesLockObjects = N.all(objects, o -> lockMap.get(o) == key);
        if (!keyContainsAllObjects || !keyMatchesLockObjects) {
            key.lock();
        }
    }

    public static void unlock(Key key) {
        N.forEach(key.getLockObjects(), o -> lockMap.remove(o));
        key.unlock();
    }

    public static void reset() {
        lockMap.clear();
    }
}
