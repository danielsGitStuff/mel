package de.mein.auth.data;

import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;

/**
 * Created by xor on 4/28/16.
 */
public abstract class ServicePayload implements SerializableEntity {

    protected String intent;
    @JsonIgnore
    protected Integer level = 1;

    public ServicePayload(String intent) {
        this.intent = intent;
    }

    public ServicePayload() {

    }

    public Integer getLevel() {
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
