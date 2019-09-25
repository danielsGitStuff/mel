package de.mel.contacts.data;

import de.mel.auth.data.ServicePayload;

/**
 * Created by xor on 10/29/17.
 */

public class NewVersionDetails extends ServicePayload {
    public NewVersionDetails() {
    }


    private Long version;

    public NewVersionDetails(Long version, String intent) {
        super(intent);
        this.version = version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Long getVersion() {
        return version;
    }
}
