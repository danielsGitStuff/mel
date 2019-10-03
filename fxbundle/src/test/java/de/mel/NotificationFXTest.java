package de.mel;

import de.mel.auth.MelNotification;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.data.db.ServiceType;
import de.mel.auth.service.MelAuthFxLoader;
import de.mel.auth.service.MelBoot;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.tools.N;
import de.mel.contacts.ContactsFXBootloader;
import de.mel.contacts.data.ContactStrings;
import de.mel.contacts.data.ContactsSettings;
import de.mel.contacts.service.ContactsService;
import de.mel.core.serialize.deserialize.collections.PrimitiveCollectionDeserializerFactory;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mel.core.serialize.serialize.fieldserializer.collections.PrimitiveCollectionSerializerFactory;
import de.mel.drive.boot.FileSyncFXBootloader;
import de.mel.sql.RWLock;
import de.mel.sql.deserialize.PairDeserializerFactory;
import de.mel.sql.serialize.PairSerializerFactory;
import javafx.embed.swing.JFXPanel;
import org.junit.Test;

import java.io.File;

public class NotificationFXTest {

    private static File BOOTLOADER_DIR = new File("notificationtest");

    @Test
    public void notification() throws Exception {
        //init
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PrimitiveCollectionSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PrimitiveCollectionDeserializerFactory.getInstance());
        new JFXPanel();
        CertificateManager.deleteDirectory(MelBoot.Companion.getDefaultWorkingDir1());
        MelAuthSettings settings = MelAuthSettings.createDefaultSettings();
        MelBoot melBoot = new MelBoot(settings, new PowerManager(settings), FileSyncFXBootloader.class, ContactsFXBootloader.class);
        melBoot.addMelAuthAdmin(new MelAuthFxLoader());
        melBoot.boot().done(mas -> N.r(() -> {
            System.out.println("NotificationFXTest.notification");
            ServiceType type = mas.getDatabaseManager().getServiceTypeByName(new ContactsFXBootloader().getName());
            ContactsFXBootloader fxBootloader = (ContactsFXBootloader) melBoot.createBootLoader(mas, ContactsFXBootloader.class);
            ContactsSettings contactsSettings = new ContactsSettings();
            contactsSettings.setRole(ContactStrings.ROLE_SERVER);
            contactsSettings.setJsonFile(new File(BOOTLOADER_DIR.getAbsoluteFile() + File.separator + "json"));
            ContactsService service = fxBootloader.createService("kekse", contactsSettings);
            MelNotification notification = new MelNotification(service.getUuid(), ContactStrings.INTENT_PROPAGATE_NEW_VERSION, "bla", "keks");
            mas.onNotificationFromService(service, notification);
            System.out.println("NotificationFXTest.notification");

        }));
        new RWLock().lockWrite().lockWrite();
    }
}
