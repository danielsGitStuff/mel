package de.mel.auth.data;

import de.mel.auth.service.Bootloader;
import de.mel.core.serialize.JsonIgnore;
import de.mel.core.serialize.SerializableEntity;

/**
 * Base class for messages (and requests) between two {@link de.mel.auth.service.MelService}s
 * Created by xor on 4/28/16.
 */
public abstract class ServicePayload implements SerializableEntity {

    /**
     * If you only want to send a small text use this one.
     */
    protected String intent;
    /**
     * This is to check whether a service has reached a sufficient boot level before handing it over to the service.
     */
    @JsonIgnore
    protected Bootloader.BootLevel level = Bootloader.BootLevel.SHORT;

    public ServicePayload(String intent) {
        this.intent = intent;
    }

    public ServicePayload() {

    }

    public Bootloader.BootLevel getLevel() {
        return level;
    }

    public ServicePayload setIntent(String intent) {
        this.intent = intent;
        return this;
    }

    public String getIntent() {
        return intent;
    }

    public boolean hasIntent(String intentQuery) {
        return intent != null && intent.equals(intentQuery);

    }
}
