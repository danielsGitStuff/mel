package de.mein.contacts.jobs;

import de.mein.auth.jobs.Job;
import de.mein.auth.socket.process.val.Request;
import de.mein.contacts.data.db.PhoneBook;
import de.mein.contacts.data.db.PhoneBookWrapper;

/**
 * Created by xor on 10/4/17.
 */

public class UpdatePhoneBookJob extends Job<Void, Void, Void> {
    private final Request request;
    private final PhoneBook phoneBook;

    public UpdatePhoneBookJob(Request phoneBook) {
        this.request = phoneBook;
        this.phoneBook = ((PhoneBookWrapper) request.getPayload()).getPhoneBook();
    }

    public Request getRequest() {
        return request;
    }

    public PhoneBook getPhoneBook() {
        return phoneBook;
    }
}
