package de.mein.android.service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import de.mein.Lok;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WatchDogTimer;

public class AndroidPowerManager extends PowerManager {
    private final android.os.PowerManager osPowerManager;
    private Map<Object, StackTraceElement[]> wakeLockCallers = new HashMap<>();
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
        Lok.debug("aquire");
        if (!wakeLockCallers.containsKey(caller)) {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            stackTrace = Arrays.copyOfRange(stackTrace, 3, stackTrace.length);
            wakeLockCallers.put(caller, stackTrace);
            wakeLock.acquire();
        }
        wakeAccessLock.unlock();
    }

    @Override
    public void releaseWakeLock(Object caller) {
        wakeAccessLock.lock();
        Lok.debug("release");
        if (wakeLockCallers.containsKey(caller)) {
            wakeLockCallers.remove(caller);
            if (wakeLockCallers.isEmpty() && wakeLock.isHeld()) {
                N.r(() -> wakeTimer.start());
            }
        } else {
            System.err.println("AndroidPowerManager.releaseWakeLock(" + caller.getClass().getName() + "): caller not registered");
        }
        wakeAccessLock.unlock();
    }

    @Deprecated
    public Object[] devGetHeldWakeLockCallers() {
        return wakeLockCallers.keySet().toArray(new Object[0]);
    }

    @Deprecated
    public StackTraceElement[] devGetCallerStackTrace(Object caller) {
        return wakeLockCallers.get(caller);
    }

}
