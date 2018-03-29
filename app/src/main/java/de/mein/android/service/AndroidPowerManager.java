package de.mein.android.service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WatchDogTimer;

public class AndroidPowerManager extends PowerManager {
    private final android.os.PowerManager osPowerManager;
    private Set<Object> wakeLockCallers = new HashSet<>();
    private Map<Object, Thread> wakeThreads = new HashMap<>();
    private Map<Object, StackTraceElement[]> wakeStacks = new HashMap<>();
    private android.os.PowerManager.WakeLock wakeLock;
    private WatchDogTimer wakeTimer;
    private ReentrantLock wakeAccessLock = new ReentrantLock(true);

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
        }, 10, 100, 1000);
    }

    @Override
    protected void onHeavyWorkAllowed() {
        N.r(() -> wakeTimer.stop());
    }

    @Override
    protected void onHeavyWorkForbidden() {
        // release the wakelock if heavy work is forbidden but the lock is held
        N.r(() -> wakeTimer.start());
    }

    @Override
    public void wakeLock(Object caller) {
        wakeAccessLock.lock();
        if (!wakeLockCallers.contains(caller)) {
            wakeLockCallers.add(caller);
            Thread thread = Thread.currentThread();
            wakeThreads.put(caller, thread);
            try {
                throw new Exception();
            } catch (Exception e) {
                StackTraceElement[] stackTrace = wakeStacks.put(caller, Arrays.copyOf(e.getStackTrace(), e.getStackTrace().length - 1));
                wakeStacks.put(caller, stackTrace);
            }
            wakeLock.acquire();
        }
        wakeAccessLock.unlock();
    }

    @Override
    public void releaseWakeLock(Object caller) {
        wakeAccessLock.lock();
        wakeThreads.remove(caller);
        wakeStacks.remove(caller);
        if (!wakeLockCallers.remove(caller)) {
            System.err.println("AndroidPowerManager.releaseWakeLock(" + caller.getClass().getName() + "): caller not registered");
            wakeAccessLock.unlock();
            return;
        }
        if (wakeLockCallers.isEmpty() && wakeLock.isHeld()) {
            N.r(() -> wakeTimer.start());
        }
        wakeAccessLock.unlock();
    }

    @Deprecated
    public Object[] devGetHeldWakeLockCallers() {
        return wakeLockCallers.toArray(new Object[0]);
    }

    public StackTraceElement[] getStack(Object caller) {
        return wakeStacks.get(caller);
    }
}
