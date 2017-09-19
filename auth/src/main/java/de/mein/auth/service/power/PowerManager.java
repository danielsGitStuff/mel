package de.mein.auth.service.power;

import java.util.concurrent.locks.ReentrantLock;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.PowerManagerSettings;
import de.mein.auth.tools.N;

/**
 * Created by xor on 9/19/17.
 */

public class PowerManager {
    public interface PowerManagerListener{
        void onHeavyWorkAllowed();
        void onHeavyWorkForbidden();
    }
    private final MeinAuthSettings meinAuthSettings;
    private final PowerManagerSettings settings;
    private boolean online = true;
    private boolean plugged = true;
    private ReentrantLock lock = new ReentrantLock(true);

    public PowerManager(MeinAuthSettings meinAuthSettings) {
        this.meinAuthSettings = meinAuthSettings;
        this.settings = meinAuthSettings.getPowerManagerSettings();
    }


    public void onCommunicationsDisabled() {
        lock.lock();
        online = false;
        lock.unlock();
    }

    public void onCommunicationsEnabled() {
        lock.lock();
        online = true;
        lock.unlock();
    }

    public void onPowerPlugged() {
        lock.lock();
        plugged = true;
        lock.unlock();
    }

    public void onPowerUnplugged() {
        lock.lock();
        plugged = false;
        lock.unlock();
    }

    public void setHeavyWorkWhenPlugged(boolean heavyWorkWhenPlugged) {
        lock.lock();
        settings.setHeavyWorkWhenPlugged(heavyWorkWhenPlugged);
        N.r(meinAuthSettings::save);
        lock.unlock();
    }

    public void setHeavyWorkWhenOffline(boolean heavyWorkWhenOffline) {
        lock.lock();
        settings.setHeavyWorkWhenOffline(heavyWorkWhenOffline);
        N.r(meinAuthSettings::save);
        lock.unlock();
    }

    public boolean heavyWorkAllowed() {
        return (settings.doHeavyWorkWhenPlugged() && plugged) || (settings.doHeavyWorkWhenOffline() && !online);
    }

    public boolean getHeavyWorkWhenOffline() {
        return settings.doHeavyWorkWhenOffline();
    }

    public boolean getHeavyWorkWhenPlugged() {
        return settings.doHeavyWorkWhenPlugged();
    }
}
