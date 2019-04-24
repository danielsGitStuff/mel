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

    public Integer getLevel() {
        return level;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getIntent() {
        return intent;
    }

}
