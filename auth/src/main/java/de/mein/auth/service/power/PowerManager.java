package de.mein.auth.service.power;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.PowerManagerSettings;
import de.mein.auth.tools.N;

/**
 * Created by xor on 9/19/17.
 */

public class PowerManager {
    public void wakeLock(Object caller) {

    }

    public void releaseWakeLock(Object caller) {

    }

    public interface PowerManagerListener {
        void onHeavyWorkAllowed();

        void onHeavyWorkForbidden();
    }

    public interface CommunicationsListener {
        void onCommunicationsEnabled();

        void onCommunicationsDisabled();
    }

    private final MeinAuthSettings meinAuthSettings;
    private final PowerManagerSettings settings;
    private boolean online = true;
    private boolean plugged = true;
    private ReentrantLock stateLock = new ReentrantLock(true);
    private ReentrantLock powerListenerLock = new ReentrantLock();
    private ReentrantLock comListenerLock = new ReentrantLock();
    private Set<PowerManagerListener> powerManagerListeners = new HashSet<>();
    private Set<CommunicationsListener> comListeners = new HashSet<>();

    public PowerManager(MeinAuthSettings meinAuthSettings) {
        this.meinAuthSettings = meinAuthSettings;
        this.settings = meinAuthSettings.getPowerManagerSettings();
    }

    public PowerManager addCommunicationListener(CommunicationsListener listener) {
        comListenerLock.lock();
        comListeners.add(listener);
        comListenerLock.unlock();
        return this;
    }

    public PowerManager removeCommunicationListener(CommunicationsListener listener) {
        comListenerLock.lock();
        comListeners.remove(listener);
        comListenerLock.unlock();
        return this;
    }

    public PowerManager addPowerListener(PowerManagerListener listener) {
        powerListenerLock.lock();
        powerManagerListeners.add(listener);
        powerListenerLock.unlock();
        return this;
    }

    public PowerManager removePowerListener(PowerManagerListener listener) {
        powerListenerLock.lock();
        powerManagerListeners.remove(listener);
        powerListenerLock.unlock();
        return this;
    }

    private void propagatePossibleStateChanges(boolean workBefore, boolean workNow) {
        try {
            powerListenerLock.lock();
            if ((workNow ^ workBefore)) {
                if (workNow)
                    for (PowerManagerListener listener : powerManagerListeners)
                        listener.onHeavyWorkAllowed();
                else
                    for (PowerManagerListener listener : powerManagerListeners)
                        listener.onHeavyWorkForbidden();
            }
        } finally {
            powerListenerLock.unlock();
        }
    }

    public void onCommunicationsDisabled() {
        stateLock.lock();
        boolean workBefore = heavyWorkAllowedNoLock();
        boolean onlineBefore = online;
        online = false;
        boolean workNow = heavyWorkAllowedNoLock();
        stateLock.unlock();
        propagatePossibleStateChanges(workBefore, workNow);
        try {
            comListenerLock.lock();
            if (onlineBefore)
                for (CommunicationsListener listener : comListeners)
                    listener.onCommunicationsDisabled();
        } finally {
            comListenerLock.unlock();
        }
    }

    public void onCommunicationsEnabled() {
        stateLock.lock();
        boolean workBefore = heavyWorkAllowedNoLock();
        boolean onlineBefore = online;
        online = true;
        boolean workNow = heavyWorkAllowedNoLock();
        stateLock.unlock();
        propagatePossibleStateChanges(workBefore, workNow);
        try {
            comListenerLock.lock();
            if (!onlineBefore)
                for (CommunicationsListener listener : comListeners)
                    listener.onCommunicationsEnabled();
        } finally {
            comListenerLock.unlock();
        }
    }

    public void onPowerPlugged() {
        stateLock.lock();
        boolean workBefore = heavyWorkAllowedNoLock();
        plugged = true;
        boolean workNow = heavyWorkAllowedNoLock();
        stateLock.unlock();
        propagatePossibleStateChanges(workBefore, workNow);
    }

    public void onPowerUnplugged() {
        stateLock.lock();
        boolean workBefore = heavyWorkAllowedNoLock();
        plugged = false;
        boolean workNow = heavyWorkAllowedNoLock();
        stateLock.unlock();
        propagatePossibleStateChanges(workBefore, workNow);
    }

    public void setHeavyWorkWhenPlugged(boolean heavyWorkWhenPlugged) {
        stateLock.lock();
        settings.setHeavyWorkWhenPlugged(heavyWorkWhenPlugged);
        N.r(meinAuthSettings::save);
        stateLock.unlock();
    }

    public void setHeavyWorkWhenOffline(boolean heavyWorkWhenOffline) {
        stateLock.lock();
        settings.setHeavyWorkWhenOffline(heavyWorkWhenOffline);
        N.r(meinAuthSettings::save);
        stateLock.unlock();
    }

    private boolean heavyWorkAllowedNoLock() {
        return (settings.doHeavyWorkWhenPlugged() && plugged) || (settings.doHeavyWorkWhenOffline() && !online);
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

    public boolean getHeavyWorkWhenPlugged() {
        return settings.doHeavyWorkWhenPlugged();
    }
}
