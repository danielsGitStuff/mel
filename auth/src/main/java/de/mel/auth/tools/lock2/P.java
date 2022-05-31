package de.mel.auth.tools.lock2;

import de.mel.Lok;
import de.mel.core.serialize.serialize.tools.StringBuilder;

import java.util.*;
import java.util.concurrent.Semaphore;

/**
 * <p>
 * Can read-/write-lock multiple objects at once.<br>
 * Create a lock, represented by {@link BunchOfLocks} by calling {@link #confine(Object... objects) P.confine}.<br>
 * {@link #confine(Object... objects) P.confine}(foo1, foo2, {@link #read(Object... objects) P.read}(foo3)) creates a lock that writes foo1 and foo2 and reads foo3.<br>
 * P.access(lock) grants access to the objects contained by lock.<br>
 * P.exit(lock) revokes the access to those objects.<br>
 * P.end(lock) revokes access and terminates the lock. It cannot be reused, because all the bookkeeping is undone.<br>
 * </p>
 * <p>
 * <ul>
 *      <li>One write-lock acquires one {@link Semaphore}. If no write is currently happening, no {@link Semaphore} is present.</li>
 *      <li>All read-locks acquire one {@link Semaphore} together.</li>
 *      <li>The first read-lock acquires the {@link Semaphore}. The following read-locks ignore it.</li>
 *      <li>A write-lock will try to acquire the {@link Semaphore} if it exists.</li>
 *      <li>If no read-locks are currently happening the {@link Semaphore} is not present.</li>
 * </ul>
 * </p>
 */
public class P {
    private static final String LOCKER = "lock string";
    /**
     * Mapping of object and the {@link Semaphore} it is write-locked by.
     */
    private static final Map<LockObjectEntry, Semaphore> writeSemaphores = new HashMap<>();
    /**
     * Every semaphore held by a {@link BunchOfLocks}.
     */
    private static final Map<BunchOfLocks, Set<Semaphore>> writeSemaphoreOwners = new HashMap<>();
    /**
     * Map of write-locked objects and the according {@link BunchOfLocks} at this moment.
     */
    private static final Map<LockObjectEntry, Set<BunchOfLocks>> activeWrites = new HashMap<>();
    /**
     * Map of write-lockable objects and {@link BunchOfLocks} that can lock them.
     */
    private static final Map<LockObjectEntry, Set<BunchOfLocks>> associatedWrite = new HashMap<>();
    /**
     * All {@link BunchOfLocks} that currently (intend to) lock something.
     */
    private static final Set<BunchOfLocks> activeBunches = new HashSet<>();
    /**
     * All {@link BunchOfLocks} that are currently successfully locked.
     */
    private static final Set<BunchOfLocks> grantedBunches = new HashSet<>();
    /**
     * All {@link BunchOfLocks} that currently exist.
     */
    private static final Set<BunchOfLocks> existingBunches = new HashSet<>();
    /**
     * Mapping of object and the {@link Semaphore} it is read-locked by.
     */
    private static final Map<LockObjectEntry, Semaphore> readSemaphores = new HashMap<>();
    /**
     * Map of read-locked objects and the according {@link BunchOfLocks} at this moment.
     */
    private static final Map<LockObjectEntry, Set<BunchOfLocks>> activeReads = new HashMap<>();
    /**
     * Map of read-lockable objects and {@link BunchOfLocks} that can lock them.
     */
    private static final Map<LockObjectEntry, Set<BunchOfLocks>> associatedRead = new HashMap<>();
    /**
     * Map of lockable objects and {@link BunchOfLocks} that can lock them.
     */
    private static final Map<LockObjectEntry, Set<BunchOfLocks>> associatedGeneral = new HashMap<>();

    private static boolean DEBUG_PRINT = false;

    public static void enableDebugPrinting() {
        DEBUG_PRINT = true;
        for (int i = 0; i < 5; i++) {
            Lok.error("P.DEBUG_PRINT=true");
        }
    }

    private static void debug(Object msg) {
        if (DEBUG_PRINT)
            Lok.debug(msg);
    }


    public static BunchOfLocks confine(Object... objects) {
        BunchOfLocks bunchOfLocks;
        synchronized (LOCKER) {
            Map<LockObjectEntry, Boolean> doubleCheck = new IdentityHashMap<>();
            bunchOfLocks = new BunchOfLocks(objects);
            bunchOfLocks.setName(getStackTraceName());
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
                if (doubleCheck.containsKey(e)) {
                    Lok.error("Same object locked multiple times!!!");
                }
                doubleCheck.put(e, true);
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
                if (doubleCheck.containsKey(e)) {
                    Lok.error("Same object locked multiple times!!!");
                }
                doubleCheck.put(e, true);
            }
            existingBunches.add(bunchOfLocks);
        }
        return bunchOfLocks;
    }

    private static String getStackTraceName() {
        Exception e;
        try {
            throw new Exception();
        } catch (Exception ex) {
            e = ex;
        }
        StackTraceElement[] st = e.getStackTrace();
        StringBuilder b = new StringBuilder();
        StackTraceElement mandatory = st[2];
        b.append(mandatory.getClassName()).comma().append(mandatory.getMethodName().subSequence(0, Integer.min(mandatory.getMethodName().length(), 4)));
        StackTraceElement obligatory = st.length > 3 ? st[3] : null;
        if (obligatory != null) {
            b.arrBegin().append(obligatory.getClassName()).comma().append(obligatory.getMethodName().subSequence(0, Integer.min(obligatory.getMethodName().length(), 4))).arrEnd();
        }
        return b.toString();
    }

    public static Read read(Object... objects) {
        return new Read(objects);
    }


    public static void access(BunchOfLocks bunchOfLocks) {
        List<Semaphore> writeLocks = new ArrayList<>();
        List<Semaphore> readLocks = new ArrayList<>();
        // always synchronize when bookkeeping is done!
        synchronized (LOCKER) {
            if (!existingBunches.contains(bunchOfLocks)) {
                Lok.error("BunchOfLocks has already been ended!");
                return;
            }
            if (grantedBunches.contains(bunchOfLocks)) {
//                Lok.error("BunchOfLocks has already access!!!");
                Lok.debug("access already granted...");
                return;
//                System.exit(2);
            }
            if (activeBunches.contains(bunchOfLocks)) {
                Lok.error("BunchOfLocks has already requested access!");
                System.exit(2);
                return;
            }
            P.debug("P.access() called from thread '" + Thread.currentThread().getName() + "'");
            // collect locks required to write
            for (LockObjectEntry e : bunchOfLocks.getWriteLocks()) {
                // if there is an active write going on, it left a semaphore for us
                if (P.activeWrites.containsKey(e)) {
                    Semaphore s = writeSemaphores.get(e);
                    writeLocks.add(s);
                } else {
                    // no active write means we must leave a semaphore here
                    Semaphore s = new Semaphore(1);
                    writeSemaphores.put(e, s);
                    // lock it
                    try {
                        s.acquire();
                        // remember which BunchOfLocks owns it, so we can release the semaphore when it exits.
                        Set<Semaphore> owned = writeSemaphoreOwners.getOrDefault(bunchOfLocks, new HashSet<>());
                        owned.add(s);
                        writeSemaphoreOwners.put(bunchOfLocks, owned);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }

                // map this LockObjectEntry to the semaphore
                P.debug("P.access() Thread '" + Thread.currentThread().getName() + "' adding " + e.getId() + "/'" + bunchOfLocks.getName() + "' to activeWrites");
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
                P.debug("P.access() Thread '" + Thread.currentThread().getName() + "' adding " + e.getId() + "/'" + bunchOfLocks.getName() + "' to activeReads");
                // a read object might be held by multiple LockObjectEntries.
                // so remember their mapping.
                P.addToEntryBunchMap(bunchOfLocks, e, activeReads);
                // if there is a writing semaphore, collect that too.
                if (writeSemaphores.containsKey(e)) {
                    readLocks.add(writeSemaphores.get(e));
                }
            }
            activeBunches.add(bunchOfLocks);
        }
        for (Semaphore s : writeLocks) {
            try {
                s.acquire();
                synchronized (LOCKER) {
                    Set<Semaphore> owned = writeSemaphoreOwners.getOrDefault(bunchOfLocks, new HashSet<>());
                    owned.add(s);
                    writeSemaphoreOwners.put(bunchOfLocks, owned);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        for (Semaphore s : readLocks) {
            try {
                s.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        synchronized (LOCKER) {
            // may have benn removed in the meantime
            if (activeBunches.contains(bunchOfLocks))
                grantedBunches.add(bunchOfLocks);
        }
    }

    public static void exit(BunchOfLocks bunchOfLocks) {
        synchronized (LOCKER) {
            if (!existingBunches.contains(bunchOfLocks))
                return;
            if (!activeBunches.contains(bunchOfLocks))
                return;
            if (!grantedBunches.contains(bunchOfLocks))
                return;
            // take care of write semaphore ownership
            if (writeSemaphoreOwners.containsKey(bunchOfLocks)) {
                // release the owned semaphores
                Set<Semaphore> owned = writeSemaphoreOwners.remove(bunchOfLocks);
                for (Semaphore s : owned)
                    s.release();
                writeSemaphoreOwners.remove(bunchOfLocks);
            }
            for (LockObjectEntry e : bunchOfLocks.getWriteLocks()) {
                P.debug("P.exit() Thread '" + Thread.currentThread().getName() + "' removing " + e.getId() + "/'" + bunchOfLocks.getName() + "' from activeWrites");
                Set<BunchOfLocks> active = activeWrites.get(e);
                active.remove(bunchOfLocks);
                if (active.isEmpty()) {
                    // nothing wants to write anymore
                    writeSemaphores.remove(e);
                    activeWrites.remove(e);
                }
            }
            for (LockObjectEntry e : bunchOfLocks.getReadLocks()) {
                // remove from currently active reads
                Set<BunchOfLocks> active = activeReads.get(e);
                P.debug("P.exit() Thread '" + Thread.currentThread().getName() + "' removing " + e.getId() + "/'" + bunchOfLocks.getName() + "' from activeReads");
                active.remove(bunchOfLocks);
                // if no BunchOfLocks is locking on it anymore the semaphore can be released and freed.
                if (active.isEmpty()) {
                    activeReads.remove(e);
                    Semaphore s = readSemaphores.get(e);
                    s.release();
                    readSemaphores.remove(e);
                }
            }
            activeBunches.remove(bunchOfLocks);
            grantedBunches.remove(bunchOfLocks);
        }
    }

    private static void removeFromEntryBunchMap(BunchOfLocks bunchOfLocks, List<LockObjectEntry> lockObjectEntries, Map<LockObjectEntry, Set<BunchOfLocks>> map) {
        for (LockObjectEntry e : lockObjectEntries) {
            Set<BunchOfLocks> associated = map.get(e);
            if (associated == null)
                Lok.debug("associated == null");
            associated.remove(bunchOfLocks);
            if (associated.isEmpty())
                map.remove(e);
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
        P.debug("P.end() Thread '" + Thread.currentThread().getName() + "' ending '" + bunchOfLocks.getName() + "'");
        synchronized (LOCKER) {
//            if (bunchOfLocks.getName().equals("de.mel.filesync.service.sync.ClientSyncHandler,sync[de.mel.filesync.service.sync.ClientSyncHandler,exec]"))
//                Lok.debug("debug mmsmef");
            if (!existingBunches.contains(bunchOfLocks))
                return;
            existingBunches.remove(bunchOfLocks);
            P.removeFromEntryBunchMap(bunchOfLocks, bunchOfLocks.getReadLocks(), associatedRead);
            P.removeFromEntryBunchMap(bunchOfLocks, bunchOfLocks.getWriteLocks(), associatedWrite);
            P.removeFromEntryBunchMap(bunchOfLocks, bunchOfLocks.getAllLocks(), associatedGeneral);
            for (LockObjectEntry e : bunchOfLocks.getAllLocks()) {
                if (!associatedGeneral.containsKey(e)) {
                    LockObjectEntry.free(e);
                }
            }
        }
    }

    public static Set<BunchOfLocks> debugWhoLocksResource(Object lockedObject) {
        LockObjectEntry entry = LockObjectEntry.debugGetInstance(lockedObject);
        return associatedGeneral.get(entry);
    }

    public static Set<BunchOfLocks> debugActiveBunches() {
        return P.activeBunches;
    }

    public static PState debugGetPState() {
        PState p = new PState();
        p.activeBunches = new HashSet<>(P.activeBunches);
        p.activeReads = new HashMap<>(P.activeReads);
        p.activeWrites = new HashMap<>(P.activeWrites);
        p.associatedRead = new HashMap<>(P.activeWrites);
        p.associatedWrite = new HashMap<>(P.associatedWrite);
        p.associatedGeneral = new HashMap<>(P.associatedGeneral);
        p.existingBunches = new HashSet<>(P.existingBunches);
        p.grantedBunches = new HashSet<>(P.grantedBunches);
        p.readSemaphores = new HashMap<>(P.readSemaphores);
        p.writeSemaphores = new HashMap<>(P.writeSemaphores);
        p.writeSemaphoreOwners = new HashMap<>(P.writeSemaphoreOwners);
        return p;
    }

}
