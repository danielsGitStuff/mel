package de.mein.android.contacts.data;

/**
 * Created by xor on 10/18/17.
 */

public class Conflict {
    private final   Long localPhoneBookId;
    private  final Long receivedPhoneBookId;

    public Conflict(Long localPhoneBookId, Long receivedPhoneBookId) {
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
