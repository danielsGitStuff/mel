package mein.de.contacts.data;

import java.io.File;
import java.io.IOException;

import de.mein.auth.data.JsonSettings;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;
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

    public String getRole() {
        return role;
    }

    public static ContactsSettings load(File jsonFile, ContactsSettings settingsCfg) throws IOException, JsonDeserializationException, JsonSerializationException, IllegalAccessException, SqlQueriesException {
        ContactsSettings contactsSettings = (ContactsSettings) JsonSettings.load(jsonFile);
        if (contactsSettings == null) {
            contactsSettings = new ContactsSettings();
            contactsSettings.setJsonFile(jsonFile);
        }
        if (settingsCfg != null) {
            contactsSettings.setRole(settingsCfg.getRole());
        }
        contactsSettings.save();
        return contactsSettings;
    }

}
