package de.mel.android.service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import de.mel.Lok;
import de.mel.android.Tools;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.tools.N;
import de.mel.auth.tools.WatchDogTimer;

public class AndroidPowerManager extends PowerManager {
    private static final String PREF_POWER_WIFI = "p.pw";
    private static final String PREF_POWER_NO_WIFI = "p.pnw";
    private static final String PREF_NO_POWER_WIFI = "p.npw";
    private static final String PREF_NO_POWER_NO_WIFI = "p.npnw";
    private Map<Object, StackTraceElement[]> wakeLockCallers = new HashMap<>();
    private android.os.PowerManager.WakeLock wakeLock;
    private WatchDogTimer wakeTimer;
    private ReentrantLock wakeAccessLock = new ReentrantLock(true);
    private boolean powerWifi = false;
    private boolean powerNoWifi = false;
    private boolean noPowerWifi = false;
    private boolean noPowerNoWifi = false;
    private Set<Object> overrideCallers = new HashSet();
    private ReentrantLock overrideAccessLock = new ReentrantLock(true);


    public AndroidPowerManager(MelAuthSettings melAuthSettings, android.os.PowerManager osPowerManager) {
        super(melAuthSettings);
        powerWifi = Tools.getSharedPreferences().getBoolean(PREF_POWER_WIFI, true);
        powerNoWifi = Tools.getSharedPreferences().getBoolean(PREF_POWER_NO_WIFI, false);
        noPowerWifi = Tools.getSharedPreferences().getBoolean(PREF_NO_POWER_WIFI, false);
        noPowerNoWifi = Tools.getSharedPreferences().getBoolean(PREF_NO_POWER_NO_WIFI, false);
        wakeLock = osPowerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
        // set this to false cause we do not want to accidentally start boot level 2.
        // the power/wifi listeners will update the values when started
        wifi = false;
        powered = false;
        wakeLock(this);
        wakeTimer = new WatchDogTimer("android power manager", () -> {
            stateLock.lock();
            changeState();
            wakeTimer.cancel();
            stateLock.unlock();
        }, 10, 100, 1000);
    }

    @Override
    public boolean heavyWorkAllowed() {
        stateLock.lock();
        boolean result = runWhen(powered, wifi);
        stateLock.unlock();
        return result;
    }

    /**
     * changes state, takes or releases wakelock if necessary.
     */
    private void changeState() {
        boolean shouldRun = runWhenOverride(powered, wifi);
        if (shouldRun != running && melAuthService != null) {
            if (shouldRun) {
                Lok.debug("resuming...");
                melAuthService.resume();
            } else {
                Lok.debug("suspending...");
                melAuthService.suspend();
            }
            running = shouldRun;
            if (running) {
                this.wakeLock(this);
            } else {
                this.releaseWakeLock(this);
            }
            N.forEach(listeners, iPowerStateListener -> iPowerStateListener.onStateChanged(AndroidPowerManager.this));
        } else {
            Lok.debug("nothing to do...");
        }
    }

    public void configure(Boolean powerWifi, Boolean powerNoWifi, Boolean noPowerWifi, Boolean noPowerNoWifi) {
        if (powerWifi != null)
            this.powerWifi = powerWifi;
        if (powerNoWifi != null)
            this.powerNoWifi = powerNoWifi;
        if (noPowerWifi != null)
            this.noPowerWifi = noPowerWifi;
        if (noPowerNoWifi != null)
            this.noPowerNoWifi = noPowerNoWifi;
        savePrefs();
    }

    private void savePrefs() {
        Tools.getSharedPreferences().edit()
                .putBoolean(PREF_POWER_WIFI, powerWifi)
                .putBoolean(PREF_POWER_NO_WIFI, powerNoWifi)
                .putBoolean(PREF_NO_POWER_WIFI, noPowerWifi)
                .putBoolean(PREF_NO_POWER_NO_WIFI, noPowerNoWifi)
                .apply();
    }

    /**
     * same as runWHen() but respects override
     *
     * @param onPower
     * @param onWifi
     * @return
     */
    public boolean runWhenOverride(boolean onPower, boolean onWifi) {
        if (!overrideCallers.isEmpty())
            return true;
        return runWhen(onPower, onWifi);
    }

    /**
     * @param onPower
     * @param onWifi
     * @return whether or not {@link AndroidPowerManager} allows communications given a (wifi + power) state
     */
    public boolean runWhen(boolean onPower, boolean onWifi) {
        if (onPower && onWifi)
            return powerWifi;
        else if (onPower)
            return powerNoWifi;
        else if (onWifi)
            return noPowerWifi;
        else
            return noPowerNoWifi;
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

    private void updateWifi(boolean wifiNow) {
        if (wifi != wifiNow) {
            wifi = wifiNow;
            boolean shouldRun = runWhenOverride(powered, wifi);
            if (shouldRun != running) {
                N.r(() -> wakeTimer.start());
            } else {
                Set<IPowerStateListener<? extends PowerManager>> listenersCopy = new HashSet<>(listeners);
                N.forEach(listenersCopy, iPowerStateListener -> iPowerStateListener.onStateChanged(AndroidPowerManager.this));
            }
        }
    }

    private void updatePowered(boolean powerNow) {
        if (powered != powerNow) {
            powered = powerNow;
            boolean shouldRun = runWhenOverride(powered, wifi);
            if (shouldRun != running) {
                N.r(() -> wakeTimer.start());
            } else {
                N.forEach(listeners, iPowerStateListener -> iPowerStateListener.onStateChanged(AndroidPowerManager.this));
            }
        }
    }

    @Override
    public void onCommunicationsDisabled() {
        stateLock.lock();
        updateWifi(false);
        stateLock.unlock();
    }

    @Override
    public void onCommunicationsEnabled() {
        stateLock.lock();
        updateWifi(true);
        stateLock.unlock();
    }

    @Override
    public void onPowerPlugged() {
        stateLock.lock();
        updatePowered(true);
        stateLock.unlock();
    }

    @Override
    public void onPowerUnplugged() {
        stateLock.lock();
        updatePowered(false);
        stateLock.unlock();
    }

    public void overrideState(Object caller) {
        /**
         * this is deactivated until suspended services have a sophisticated way to deal with messages and requests.
         * for now all services are expected to be running when the auth instance is reachable.
         *
         */
        overrideAccessLock.lock();
        this.overrideCallers.add(caller);
        N.r(() -> wakeTimer.waite());
//        N.r(() -> wakeTimer.stop());
        overrideAccessLock.unlock();
        changeState();
    }

    public void releaseOverride(Object caller) {
        overrideAccessLock.lock();
        boolean foundCaller = this.overrideCallers.remove(caller);
        if (foundCaller && this.overrideCallers.isEmpty()) {
            N.r(() -> wakeTimer.resume());
            N.r(() -> wakeTimer.start());
        }
        overrideAccessLock.unlock();
    }

    @Override
    public void wakeLock(Object caller) {
//        Lok.debug("aquire");
        wakeAccessLock.lock();
//        Lok.debug("aquired");
        try {
            if (!wakeLockCallers.containsKey(caller)) {
                StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                stackTrace = Arrays.copyOfRange(stackTrace, 3, stackTrace.length);
                wakeLockCallers.put(caller, stackTrace);
                wakeLock.acquire();
            }
        } finally {
            wakeAccessLock.unlock();
        }
    }

    @Override
    public void releaseWakeLock(Object caller) {
        wakeAccessLock.lock();
//        Lok.debug("release");
        if (wakeLockCallers.containsKey(caller)) {
            wakeLockCallers.remove(caller);
            if (wakeLockCallers.isEmpty() && wakeLock.isHeld()) {
                wakeLock.release();
//                N.r(() -> wakeTimer.start());
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

    public void togglePowerWifi() {
        stateLock.lock();
        powerWifi = !powerWifi;
        savePrefs();
        changeState();
        stateLock.unlock();
    }

    public void togglePowerNoWifi() {
        stateLock.lock();
        powerNoWifi = !powerNoWifi;
        savePrefs();
        changeState();
        stateLock.unlock();
    }

    public void toggleNoPowerWifi() {
        stateLock.lock();
        noPowerWifi = !noPowerWifi;
        savePrefs();
        changeState();
        stateLock.unlock();
    }

    public void toggleNoPowerNoWifi() {
        stateLock.lock();
        noPowerNoWifi = !noPowerNoWifi;
        savePrefs();
        changeState();
        stateLock.unlock();
    }

    public boolean getNoPowerNoWifi() {
        return noPowerNoWifi;
    }

    public boolean getNoPowerWifi() {
        return noPowerWifi;
    }

    public boolean getPowerNoWifi() {
        return powerNoWifi;
    }

    public boolean getPowerWifi() {
        return powerWifi;
    }
}
