package de.mein.android.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WatchDogTimer;

public class AndroidPowerManager extends PowerManager {
    private final android.os.PowerManager osPowerManager;
    private Set<Object> wakeLockCallers = new HashSet<>();
    private Map<Object, Thread> wakeThreads = new HashMap<>();
    private android.os.PowerManager.WakeLock wakeLock;
    private WatchDogTimer wakeTimer;

    public AndroidPowerManager(MeinAuthSettings meinAuthSettings, android.os.PowerManager osPowerManager) {
        super(meinAuthSettings);
        this.osPowerManager = osPowerManager;
        wakeLock = osPowerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        wakeTimer = new WatchDogTimer(() -> {
            stateLock.lock();
            boolean workBefore = heavyWorkAllowedNoLock();
            boolean onlineBefore = online;
            online = false;
            boolean workNow = heavyWorkAllowedNoLock();
            stateLock.unlock();
            propagatePossibleStateChanges(workBefore, workNow);
        }, 30, 100, 1000);
    }

    @Override
    protected void onHeavyWorkAllowed() {
        N.r(()->wakeTimer.stop());
    }

    @Override
    protected void onHeavyWorkForbidden() {
        // release the wakelock if heavy work is forbidden but the lock is held
        N.r(() -> wakeTimer.start());
    }

    @Override
    public void wakeLock(Object caller) {
        if (!wakeLockCallers.contains(caller)) {
            wakeLockCallers.add(caller);
            Thread thread = Thread.currentThread();
            wakeThreads.put(caller, thread);
            wakeLock.acquire();
        }
    }

    @Override
    public void releaseWakeLock(Object caller) {
        wakeThreads.remove(caller);
        if (!wakeLockCallers.remove(caller)) {
            System.err.println("AndroidPowerManager.releaseWakeLock(" + caller.getClass().getName() + "): caller not registered");
        }
        if (wakeLockCallers.isEmpty() && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}
