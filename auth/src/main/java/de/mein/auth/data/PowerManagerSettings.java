package de.mein.auth.data;

import de.mein.core.serialize.SerializableEntity;

/**
 * Created by xor on 9/19/17.
 */

public class PowerManagerSettings implements SerializableEntity {
    private boolean heavyWorkWhenOffline = true;
    private boolean heavyWorkWhenPlugged = true;

    public PowerManagerSettings setHeavyWorkWhenOffline(boolean heavyWorkWhenOffline) {
        this.heavyWorkWhenOffline = heavyWorkWhenOffline;
        return this;
    }

    public PowerManagerSettings setHeavyWorkWhenPlugged(boolean heavyWorkWhenPlugged) {
        this.heavyWorkWhenPlugged = heavyWorkWhenPlugged;
        return this;
    }

    public boolean doHeavyWorkWhenOffline() {
        return heavyWorkWhenOffline;
    }

    public boolean doHeavyWorkWhenPlugged() {
        return heavyWorkWhenPlugged;
    }
}
