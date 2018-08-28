package de.mein.contacts.data;

import de.mein.auth.data.IPayload;

/**
 * Created by xor on 10/29/17.
 */

public class NewVersionDetails implements IPayload {
    public NewVersionDetails() {
    }

    public NewVersionDetails(Long version) {
        this.version = version;
    }

    private Long version;

    public void setVersion(Long version) {
        this.version = version;
    }

    public Long getVersion() {
        return version;
    }
}
