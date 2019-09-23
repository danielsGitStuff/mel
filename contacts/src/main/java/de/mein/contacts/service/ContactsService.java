package de.mein.contacts.service;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import de.mein.Lok;
import de.mein.auth.MeinNotification;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.service.Bootloader;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinService;
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess;
import de.mein.auth.tools.N;
import de.mein.contacts.data.ContactsSettings;
import de.mein.contacts.data.db.ContactsDatabaseManager;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;

/**
 * this service is not executed as a Runnable. Since phonebooks usually are relatively small the service does not need to run in a separate thread.
 * Created by xor on 9/21/17.
 */
public abstract class ContactsService extends MeinService {


    protected final ContactsDatabaseManager databaseManager;
    protected final ContactsSettings settings;

    public ContactsService(MeinAuthService meinAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid, ContactsSettings settingsCfg) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        super(meinAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, Bootloader.BootLevel.SHORT);
        databaseManager = new ContactsDatabaseManager(this, serviceInstanceWorkingDirectory, settingsCfg);
        settings = databaseManager.getSettings();
        //meinAuthService.execute(this);
    }

    public ContactsSettings getSettings() {
        return settings;
    }

    public ContactsDatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    @Override
    public void connectionAuthenticated(Certificate partnerCertificate) {
        Lok.debug("ContactsService.connectionAuthenticated");
    }

    @Override
    public void handleCertificateSpotted(Certificate partnerCertificate) {
        Lok.debug("ContactsService.handleCertificateSpotted");
    }

    @Override
    public void onServiceRegistered() {
        Lok.debug("ContactsService.onServiceRegistered");
    }

    @Override
    public void resume() {
        Lok.debug("resume");
        super.resume();
    }

    @Override
    public MeinNotification createSendingNotification() {
        return null;
    }

    @Override
    public void onCommunicationsDisabled() {
        Lok.debug("communications on");
    }

    @Override
    public void onCommunicationsEnabled() {
        Lok.debug("communications off");
    }

    @Override
    public void onIsolatedConnectionEstablished(MeinIsolatedProcess isolatedProcess) {

    }

    @Override
    public void onBootLevel1Finished() {
        N.r(() -> startedPromise.resolve(this));
    }

    @Override
    public void onBootLevel2Finished() {

    }

    public abstract void onContactsChanged();
}
