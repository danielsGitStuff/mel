package mein.de.contacts.data;

import de.mein.auth.data.JsonSettings;
import mein.de.contacts.service.ContactsServerService;

/**
 * Created by xor on 9/21/17.
 */

public class ContactsSettings extends JsonSettings {
    private String role = ":(";
    private ContactsServerSettings serverSettings;
    private ContactsClientSettings clientSettings;

    public boolean isServer() {
        return role.equals(ContactsStrings.ROLE_SERVER);
    }

    public ContactsSettings setRole(String role) {
        this.role = role;
        if (role.equals(ContactsStrings.ROLE_CLIENT) && clientSettings == null)
            clientSettings = new ContactsClientSettings();
        else if (role.equals(ContactsStrings.ROLE_SERVER) && serverSettings == null)
            serverSettings = new ContactsServerSettings();
        return this;
    }

}
