package de.mel.auth.tools.lock3;

import de.mel.auth.tools.lock.Warden;
import de.mel.auth.tools.lock2.Read;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BunchOfLocks {
    private List<LockObjectEntry> readLocks = new ArrayList<>();
    private List<LockObjectEntry> writeLocks = new ArrayList<>();

    private static final Map<Object, LockObjectEntry> globalReadObjectLockMap = new HashMap<>();
    private static final Map<Object, LockObjectEntry> globalWriteObjectLockMap = new HashMap<>();


    public BunchOfLocks(Object... objects) {
        for (Object o : objects) {
            if (o instanceof Read) {
                Read read = (Read) o;
                for (Object oo : read.getObjects()) {
                    LockObjectEntry entry = globalReadObjectLockMap.getOrDefault(o, LockObjectEntry.create(oo));
                    readLocks.add(entry);
                }
            } else {
                LockObjectEntry entry = globalWriteObjectLockMap.getOrDefault(o, LockObjectEntry.create(o));
                writeLocks.add(entry);
            }
            readLocks = readLocks.stream().sorted().collect(Collectors.toList());
            writeLocks = writeLocks.stream().sorted().collect(Collectors.toList());
        }
    }

    public BunchOfLocks run(Warden.TransactionRunnable runnable) {

        try {
            runnable.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public List<LockObjectEntry> getReadLocks() {
        return readLocks;
    }

    public List<LockObjectEntry> getWriteLocks() {
        return writeLocks;
    }

    public List<LockObjectEntry> getAllLocks() {
        List<LockObjectEntry> list = new ArrayList<>(this.writeLocks);
        list.addAll(readLocks);
        return list;
    }
}
