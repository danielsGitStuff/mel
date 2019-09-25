package de.mel.contacts.jobs;

import de.mel.auth.jobs.Job;
import de.mel.auth.socket.process.val.Request;
import de.mel.contacts.data.db.PhoneBook;
import de.mel.contacts.data.db.PhoneBookWrapper;

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
