package de.mein.auth.service.power;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.PowerManagerSettings;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.N;

/**
 * This class keeps track of the systems power state and shuts down or suspends connections and services.
 * Created by xor on 9/19/17.
 */
public class PowerManager {
    private final MeinAuthSettings meinAuthSettings;
    private final PowerManagerSettings settings;
    protected MeinAuthService meinAuthService;
    protected boolean wifi = false;
    protected ReentrantLock stateLock = new ReentrantLock(true);
    protected boolean powered = false;
    protected boolean running = true;
    private ReentrantLock powerListenerLock = new ReentrantLock();
    private ReentrantLock comListenerLock = new ReentrantLock();
    //    private Set<PowerManagerListener> powerManagerListeners = new HashSet<>();
    private Set<CommunicationsListener> comListeners = new HashSet<>();
    protected Set<IPowerStateListener<? extends PowerManager>> listeners = new HashSet<>();

    public PowerManager(MeinAuthSettings meinAuthSettings) {
        this.meinAuthSettings = meinAuthSettings;
        this.settings = meinAuthSettings.getPowerManagerSettings();
    }

    public PowerManager setMeinAuthService(MeinAuthService meinAuthService) {
        this.meinAuthService = meinAuthService;
        return this;
    }

    public void wakeLock(Object caller) {
        //nothing to do here
    }

    public void releaseWakeLock(Object caller) {
        //nothing to do here
    }

    public PowerManager addCommunicationListener(CommunicationsListener listener) {
        comListenerLock.lock();
        comListeners.add(listener);
        comListenerLock.unlock();
        return this;
    }

    /**
     * useful for android only
     */
    protected void onHeavyWorkAllowed() {

    }

    /**
     * useful for android only
     */
    protected void onHeavyWorkForbidden() {

    }

    public void onCommunicationsDisabled() {
    }

    public void onCommunicationsEnabled() {
    }

    public void onPowerPlugged() {
    }

    public void onPowerUnplugged() {
    }

    protected boolean heavyWorkAllowedNoLock() {
        return (settings.doHeavyWorkWhenPlugged() && powered) || (settings.doHeavyWorkWhenOffline() && !wifi);
    }

    public boolean heavyWorkAllowed() {
        stateLock.lock();
        boolean work = heavyWorkAllowedNoLock();
        stateLock.unlock();
        return work;
    }

    public boolean getHeavyWorkWhenOffline() {
        return settings.doHeavyWorkWhenOffline();
    }

    public void setHeavyWorkWhenOffline(boolean heavyWorkWhenOffline) {
        stateLock.lock();
        settings.setHeavyWorkWhenOffline(heavyWorkWhenOffline);
        N.r(meinAuthSettings::save);
        stateLock.unlock();
    }

    public boolean getHeavyWorkWhenPlugged() {
        return settings.doHeavyWorkWhenPlugged();
    }

    public void setHeavyWorkWhenPlugged(boolean heavyWorkWhenPlugged) {
        stateLock.lock();
        settings.setHeavyWorkWhenPlugged(heavyWorkWhenPlugged);
        N.r(meinAuthSettings::save);
        stateLock.unlock();
    }

    public <T extends PowerManager> void addStateListener(IPowerStateListener<T> listener) {
        this.listeners.add(listener);
    }

    public <T extends PowerManager> void removeListener(IPowerStateListener<T> listener) {
        this.listeners.remove(listener);
    }

    public interface CommunicationsListener {
        void onCommunicationsEnabled();

        void onCommunicationsDisabled();
    }

    public static interface IPowerStateListener<PowerManager> {
        void onStateChanged(de.mein.auth.service.power.PowerManager powerManager);
    }

    public boolean isPowered() {
        return powered;
    }

    public boolean isWifi() {
        return wifi;
    }
}
