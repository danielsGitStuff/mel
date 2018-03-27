package de.mein.android.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.service.power.PowerManager;

public class AndroidPowerManager extends PowerManager {
    private final android.os.PowerManager osPowerManager;
    private Set<Object> wakeLockCallers = new HashSet<>();
    private Map<Object, Thread> wakeThreads = new HashMap<>();
    private android.os.PowerManager.WakeLock wakeLock;

    public AndroidPowerManager(MeinAuthSettings meinAuthSettings, android.os.PowerManager osPowerManager) {
        super(meinAuthSettings);
        this.osPowerManager = osPowerManager;
        wakeLock = osPowerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
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
