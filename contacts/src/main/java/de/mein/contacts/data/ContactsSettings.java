package de.mein.contacts.data;

import java.io.File;
import java.io.IOException;

import de.mein.auth.data.JsonSettings;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;

/**
 * Created by xor on 9/21/17.
 */

public class ContactsSettings <T extends PlatformContactSettings> extends JsonSettings {
    private String role = ":(";
    private ContactsServerSettings serverSettings;
    private ContactsClientSettings clientSettings;
    private Long masterPhoneBookId;
    private T platformContactSettings;

    public boolean isServer() {
        return role.equals(ContactStrings.ROLE_SERVER);
    }

    public ContactsSettings setRole(String role) {
        this.role = role;
        if (role.equals(ContactStrings.ROLE_CLIENT) && clientSettings == null)
            clientSettings = new ContactsClientSettings();
        else if (role.equals(ContactStrings.ROLE_SERVER) && serverSettings == null)
            serverSettings = new ContactsServerSettings();
        return this;
    }

    public T getPlatformContactSettings() {
        return platformContactSettings;
    }

    public void setPlatformContactSettings(T platformContactSettings) {
        this.platformContactSettings = platformContactSettings;
    }

    public ContactsClientSettings getClientSettings() {
        return clientSettings;
    }

    public ContactsServerSettings getServerSettings() {
        return serverSettings;
    }

    public ContactsSettings<T> setMasterPhoneBookId(Long masterPhoneBookId) {
        this.masterPhoneBookId = masterPhoneBookId;
        return this;
    }

    public Long getMasterPhoneBookId() {
        return masterPhoneBookId;
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

    @Override
    protected void init() {

    }
}
