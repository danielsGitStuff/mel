package de.mein.android.contacts.data;

import de.mein.core.serialize.SerializableEntity;

/**
 * Created by xor on 10/18/17.
 */

public class ConflictIntentExtra implements SerializableEntity {
    private Long localPhoneBookId;
    private Long receivedPhoneBookId;

    public ConflictIntentExtra(Long localPhoneBookId, Long receivedPhoneBookId) {
        this.localPhoneBookId = localPhoneBookId;
        this.receivedPhoneBookId = receivedPhoneBookId;
    }

    public ConflictIntentExtra() {

    }

    public Long getLocalPhoneBookId() {
        return localPhoneBookId;
    }

    public Long getReceivedPhoneBookId() {
        return receivedPhoneBookId;
    }
}
