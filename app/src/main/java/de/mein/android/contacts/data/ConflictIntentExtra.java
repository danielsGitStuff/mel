package de.mein.android.contacts.data;

import de.mein.core.serialize.SerializableEntity;

/**
 * Created by xor on 10/18/17.
 */

public class ConflictIntentExtra implements SerializableEntity {
    private final   Long localPhoneBookId;
    private  final Long receivedPhoneBookId;

    public ConflictIntentExtra(Long localPhoneBookId, Long receivedPhoneBookId) {
        this.localPhoneBookId = localPhoneBookId;
        this.receivedPhoneBookId = receivedPhoneBookId;
    }

    public Long getLocalPhoneBookId() {
        return localPhoneBookId;
    }

    public Long getReceivedPhoneBookId() {
        return receivedPhoneBookId;
    }
}
