package de.mel.auth.tools.lock2;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

public class PState {
    /**
     * Mapping of object and the {@link Semaphore} it is write-locked by.
     */
    public Map<LockObjectEntry, Semaphore> writeSemaphores = new HashMap<>();
    /**
     * Every semaphore held by a {@link BunchOfLocks}.
     */
    public Map<BunchOfLocks, Set<Semaphore>> writeSemaphoreOwners = new HashMap<>();
    /**
     * Map of write-locked objects and the according {@link BunchOfLocks} at this moment.
     */
    public Map<LockObjectEntry, Set<BunchOfLocks>> activeWrites = new HashMap<>();
    /**
     * Map of write-lockable objects and {@link BunchOfLocks} that can lock them.
     */
    public Map<LockObjectEntry, Set<BunchOfLocks>> associatedWrite = new HashMap<>();
    /**
     * All {@link BunchOfLocks} that currently (intend to) lock something.
     */
    public Set<BunchOfLocks> activeBunches = new HashSet<>();
    /**
     * All {@link BunchOfLocks} that are currently successfully locked.
     */
    public Set<BunchOfLocks> grantedBunches = new HashSet<>();
    /**
     * All {@link BunchOfLocks} that currently exist.
     */
    public Set<BunchOfLocks> existingBunches = new HashSet<>();
    /**
     * Mapping of object and the {@link Semaphore} it is read-locked by.
     */
    public Map<LockObjectEntry, Semaphore> readSemaphores = new HashMap<>();
    /**
     * Map of read-locked objects and the according {@link BunchOfLocks} at this moment.
     */
    public Map<LockObjectEntry, Set<BunchOfLocks>> activeReads = new HashMap<>();
    /**
     * Map of read-lockable objects and {@link BunchOfLocks} that can lock them.
     */
    public Map<LockObjectEntry, Set<BunchOfLocks>> associatedRead = new HashMap<>();
    /**
     * Map of lockable objects and {@link BunchOfLocks} that can lock them.
     */
    public Map<LockObjectEntry, Set<BunchOfLocks>> associatedGeneral = new HashMap<>();


}
