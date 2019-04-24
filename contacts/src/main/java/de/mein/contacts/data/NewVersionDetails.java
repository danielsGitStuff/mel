package de.mein.contacts.data;

import de.mein.auth.data.ServicePayload;

/**
 * Created by xor on 10/29/17.
 */

public class NewVersionDetails extends ServicePayload {
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
