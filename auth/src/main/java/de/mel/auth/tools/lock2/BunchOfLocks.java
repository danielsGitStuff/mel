package de.mel.auth.tools.lock2;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import de.mel.auth.tools.N;

public class BunchOfLocks {
    private List<LockObjectEntry> readLocks = new ArrayList<>();
    private List<LockObjectEntry> writeLocks = new ArrayList<>();
    private String name = null;

    private List<LockRunnables.TransactionRunnable> afterRunnables = new ArrayList<>();


    public BunchOfLocks(Object... objects) {
        for (Object o : objects) {
            if (o instanceof Read) {
                Read read = (Read) o;
                for (Object oo : read.getObjects()) {
//                    LockObjectEntry entry = globalReadObjectLockMap.getOrDefault(o, LockObjectEntry.create(oo));
                    LockObjectEntry entry = LockObjectEntry.create(oo);
                    readLocks.add(entry);
                }
            } else {
//                LockObjectEntry entry = globalWriteObjectLockMap.getOrDefault(o, LockObjectEntry.create(o));
                LockObjectEntry entry = LockObjectEntry.create(o);
                writeLocks.add(entry);
            }
            readLocks = readLocks.stream().sorted().collect(Collectors.toList());
            writeLocks = writeLocks.stream().sorted().collect(Collectors.toList());
        }
    }

    @Override
    public String toString() {
        return "BOL " + (this.name == null ? "" : this.name);
    }

    public BunchOfLocks run(LockRunnables.TransactionRunnable runnable) {
        try {
            P.access(this);
            runnable.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            P.exit(this);
        }
        return this;
    }

    /**
     * Convenience function that runs the runnable after locking and returns the result.
     * The {@link BunchOfLocks} ends afterwards!
     *
     * @param resultRunnable
     * @param <T>
     * @return
     */
    public <T> T runResult(LockRunnables.TransactionResultRunnable<T> resultRunnable) {
        T t = null;
        try {
            P.access(this);
            t = resultRunnable.run();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            P.end(this);
        }
        return t;
    }

    public BunchOfLocks end() {
        P.end(this);
        N.forEachIgnorantly(this.afterRunnables, LockRunnables.TransactionRunnable::run);
        return this;
    }

    public void after(LockRunnables.TransactionRunnable after) {
        this.afterRunnables.add(after);
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

    public String getName() {
        return name == null ? "not set" : name;
    }

    public BunchOfLocks setName(String name) {
        this.name = name;
        return this;
    }
}
