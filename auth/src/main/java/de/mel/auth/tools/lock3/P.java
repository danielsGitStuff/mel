package de.mel.auth.tools.lock3;

import de.mel.auth.tools.lock2.Read;

import java.util.*;
import java.util.concurrent.Semaphore;

public class P {
    private static final String LOCKER = "lock string";

    private static final Map<LockObjectEntry, Semaphore> writeSemaphores = new HashMap<>();
    private static final Map<LockObjectEntry, Set<BunchOfLocks>> activeWrites = new HashMap<>();
    private static final Map<LockObjectEntry, Set<BunchOfLocks>> associatedWrite = new HashMap<>();
    private static final Set<BunchOfLocks> activeBunches = new HashSet<>();

    private static final Map<LockObjectEntry, Semaphore> readSemaphores = new HashMap<>();
    private static final Map<LockObjectEntry, Set<BunchOfLocks>> activeReads = new HashMap<>();
    private static final Map<LockObjectEntry, Set<BunchOfLocks>> associatedRead = new HashMap<>();

    private static final Map<LockObjectEntry, Set<BunchOfLocks>> associatedGeneral = new HashMap<>();


    public static BunchOfLocks confine(Object... objects) {
        BunchOfLocks bunchOfLocks;
        synchronized (LOCKER) {
            bunchOfLocks = new BunchOfLocks(objects);
            // Do some bookkeeping, so we know whether we should keep semaphores and LockObjectEntries.
            // If not referenced anymore, LockObjectEntries can be freed.
            for (LockObjectEntry e : bunchOfLocks.getWriteLocks()) {
                // Store every BunchOfLocks that writes to a certain object.
                Set<BunchOfLocks> associated = associatedWrite.getOrDefault(e, new HashSet<>());
                associated.add(bunchOfLocks);
                associatedWrite.put(e, associated);
                // Do the same for the general map.
                // If a Set<BunchOfLocks> becomes empty here, the according LockObjectEntry ca be freed.
                associated = associatedGeneral.getOrDefault(e, new HashSet<>());
                associated.add(bunchOfLocks);
                associatedGeneral.put(e, associated);
            }
            // repeat for read objects
            for (LockObjectEntry e : bunchOfLocks.getReadLocks()) {
                Set<BunchOfLocks> associated = associatedRead.getOrDefault(e, new HashSet<>());
                associated.add(bunchOfLocks);
                associatedRead.put(e, associated);
                // same for general map
                associated = associatedGeneral.getOrDefault(e, new HashSet<>());
                associated.add(bunchOfLocks);
                associatedGeneral.put(e, associated);
            }
            activeBunches.add(bunchOfLocks);
        }
        return bunchOfLocks;
    }

    public static Read read(Object... objects) {
        return new Read(objects);
    }


    public static void access(BunchOfLocks bunchOfLocks) {
        List<Semaphore> writeLocks = new ArrayList<>();
        List<Semaphore> readLocks = new ArrayList<>();
        synchronized (LOCKER) {
            // collect locks required to write
            for (LockObjectEntry e : bunchOfLocks.getWriteLocks()) {
                // if there is an active write going on, it left a semaphore for us
                if (P.activeWrites.containsKey(e)){
                    Semaphore s = writeSemaphores.get(e);
                    writeLocks.add(s);
                }else {
                    // no active write means we must leave a semaphore here
                    Semaphore s = new Semaphore(1);
                    writeSemaphores.put(e, s);
                    // lock it
                    try {
                        s.acquire();
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                // map this LockObjectEntry to the semaphore
                P.addToEntryBunchMap(bunchOfLocks, e, activeWrites);

                // if there is a reading semaphore active, collect that too.
                if (readSemaphores.containsKey(e))
                    writeLocks.add(readSemaphores.get(e));
            }
            // collect locks required to read
            for (LockObjectEntry e : bunchOfLocks.getReadLocks()) {
                // there is ONE semaphore FOR ALL reads.
                if (!readSemaphores.containsKey(e)) {
                    // if no read is active there is no semaphore, so create one.
                    Semaphore s = new Semaphore(1);
                    // semaphore has to be locked, such that it cannot be acquired for writing.
                    try {
                        s.acquire();
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                    // ma[ this LockObjectEntry to the semaphore
                    readSemaphores.put(e, s);
                }
                // a read object might be held by multiple LockObjectEntries.
                // so remember their mapping.
                P.addToEntryBunchMap(bunchOfLocks, e, activeReads);
                // if there is a writing semaphore, collect that too.
                if (writeSemaphores.containsKey(e)) {
                    readLocks.add(writeSemaphores.get(e));
                }
            }
        }
        List<Semaphore> semaphores = new ArrayList<>(writeLocks);
        semaphores.addAll(readLocks);
        for (Semaphore s : semaphores) {
            try {
                s.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void exit(BunchOfLocks bunchOfLocks) {
        synchronized (LOCKER) {
            if (!activeBunches.contains(bunchOfLocks))
                return;
            for (LockObjectEntry e : bunchOfLocks.getWriteLocks()) {
                Set<BunchOfLocks> active = activeWrites.get(e);
                active.remove(bunchOfLocks);
                // that semaphore is not
                if (active.isEmpty()) {
                    // There is only one semaphore that has to be released and freed for writes.
                    Semaphore s = writeSemaphores.get(e);
                    s.release();
                    writeSemaphores.remove(e);
                    activeWrites.remove(e);
                }
            }
            for (LockObjectEntry e : bunchOfLocks.getReadLocks()) {
                // remove from currently active reads
                Set<BunchOfLocks> active = activeReads.get(e);
                active.remove(bunchOfLocks);
                // if no BunchOfLocks is locking on it anymore the semaphore can be released and freed.
                if (active.isEmpty()) {
                    activeReads.remove(e);
                    Semaphore s = readSemaphores.get(e);
                    s.release();
                    readSemaphores.remove(e);
                }
            }
        }
    }

    private static void removeFromEntryBunchMap(BunchOfLocks bunchOfLocks, List<LockObjectEntry> lockObjectEntries, Map<LockObjectEntry, Set<BunchOfLocks>> map) {
        for (LockObjectEntry e : lockObjectEntries) {
            Set<BunchOfLocks> associated = map.get(e);
            associated.remove(bunchOfLocks);
            if (associated.isEmpty())
                map.remove(e);
//            associated = associatedGeneral.get(e);
//            associated.remove(bunchOfLocks);
//            if (associated.isEmpty())
//                associatedGeneral.remove(e);
        }
    }

    private static void addToEntryBunchMap(BunchOfLocks bunchOfLocks, LockObjectEntry e, Map<LockObjectEntry, Set<BunchOfLocks>> map) {
        if (map.containsKey(e))
            map.get(e).add(bunchOfLocks);
        else {
            Set<BunchOfLocks> active = new HashSet<>();
            active.add(bunchOfLocks);
            map.put(e, active);
        }
    }

    public static void end(BunchOfLocks bunchOfLocks) {
        exit(bunchOfLocks);
        synchronized (LOCKER) {
            if (!activeBunches.contains(bunchOfLocks))
                return;
            activeBunches.remove(bunchOfLocks);
            P.removeFromEntryBunchMap(bunchOfLocks, bunchOfLocks.getReadLocks(), associatedRead);
            P.removeFromEntryBunchMap(bunchOfLocks, bunchOfLocks.getWriteLocks(), associatedWrite);
            P.removeFromEntryBunchMap(bunchOfLocks, bunchOfLocks.getAllLocks(), associatedGeneral);
        }
    }
}
