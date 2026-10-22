package de.mel.filesync.service.sync;

import de.mel.auth.tools.N;
import de.mel.auth.tools.WatchDogTimer;
import de.mel.filesync.data.AvailableHashes;

import java.util.concurrent.locks.ReentrantLock;

public class HashAvailTimer extends WatchDogTimer {
    /**
     * How long the server collects hashes that became available before it tells the clients: 100 ms, then this many periods without a new one (about 10 s).
     * The app keeps the default; tests make it short. Read when a timer is made.
     */
    public static volatile int quietRepetitions = 10;
    public static volatile int quietPeriodMs = 1000;

    private AvailableHashes hashesAvailable = new AvailableHashes();
    private ReentrantLock stateLock = new ReentrantLock(true);

    /**
     * @param watchDogTimerFinished
     */
    public HashAvailTimer(WatchDogTimerFinished watchDogTimerFinished) {
        super(HashAvailTimer.class.getSimpleName(), watchDogTimerFinished, quietRepetitions, 100, quietPeriodMs);
        setWatchDogTimerFinished(() -> {
            stateLock.lock();
            watchDogTimerFinished.onTimerStopped();
            hashesAvailable.clear();
            stateLock.unlock();
        });
    }

    public synchronized void addHash(String hash) {
        stateLock.lock();
        N.r(this::start);
        hashesAvailable.addHash(hash);
        stateLock.unlock();
    }

    public AvailableHashes getHashesAvailableCopy() {
        AvailableHashes copy = new AvailableHashes();
        copy.getHashes().addAll(hashesAvailable.getHashes());
        return copy;
    }
}
