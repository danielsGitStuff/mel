package de.mel.contacts.service;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import de.mel.Lok;
import de.mel.auth.MelNotification;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.service.Bootloader;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.service.MelAuthServiceImpl;
import de.mel.auth.service.MelService;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
import de.mel.auth.tools.N;
import de.mel.contacts.data.ContactsSettings;
import de.mel.contacts.data.db.ContactsDatabaseManager;
import de.mel.core.serialize.exceptions.JsonDeserializationException;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.sql.SqlQueriesException;

/**
 * this service is not executed as a Runnable. Since phonebooks usually are relatively small the service does not need to run in a separate thread.
 * Created by xor on 9/21/17.
 */
public abstract class ContactsService extends MelService {


    protected final ContactsDatabaseManager databaseManager;
    protected final ContactsSettings settings;

    public ContactsService(MelAuthService melAuthService, File serviceInstanceWorkingDirectory, Long serviceTypeId, String uuid, ContactsSettings settingsCfg) throws JsonDeserializationException, JsonSerializationException, IOException, SQLException, SqlQueriesException, IllegalAccessException, ClassNotFoundException {
        super(melAuthService, serviceInstanceWorkingDirectory, serviceTypeId, uuid, Bootloader.BootLevel.SHORT);
        databaseManager = new ContactsDatabaseManager(this, serviceInstanceWorkingDirectory, settingsCfg);
        settings = databaseManager.getSettings();
        //melAuthService.execute(this);
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
    public MelNotification createSendingNotification() {
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
    public void onIsolatedConnectionEstablished(MelIsolatedProcess isolatedProcess) {

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
