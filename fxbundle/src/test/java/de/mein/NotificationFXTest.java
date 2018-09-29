package de.mein;

import de.mein.auth.MeinAuthAdmin;
import de.mein.auth.MeinNotification;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.service.BootLoader;
import de.mein.auth.service.MeinAuthFxLoader;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.auth.tools.N;
import de.mein.contacts.ContactsFXBootloader;
import de.mein.contacts.data.ContactStrings;
import de.mein.contacts.data.ContactsSettings;
import de.mein.contacts.service.ContactsService;
import de.mein.core.serialize.deserialize.collections.PrimitiveCollectionDeserializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.core.serialize.serialize.fieldserializer.collections.PrimitiveCollectionSerializerFactory;
import de.mein.drive.DriveBootLoader;
import de.mein.drive.boot.DriveFXBootLoader;
import de.mein.sql.RWLock;
import de.mein.sql.deserialize.PairDeserializerFactory;
import de.mein.sql.serialize.PairSerializerFactory;
import javafx.embed.swing.JFXPanel;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
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
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);
        MeinAuthSettings settings = MeinAuthSettings.createDefaultSettings();
        MeinBoot meinBoot = new MeinBoot(settings, new PowerManager(settings), DriveFXBootLoader.class, ContactsFXBootloader.class);
        meinBoot.addMeinAuthAdmin(new MeinAuthFxLoader());
        meinBoot.boot().done(mas -> N.r(() -> {
            System.out.println("NotificationFXTest.notification");
            ServiceType type = mas.getDatabaseManager().getServiceTypeByName(new ContactsFXBootloader().getName());
            ContactsFXBootloader fxBootloader = (ContactsFXBootloader) meinBoot.createBootLoader(mas, ContactsFXBootloader.class);
            ContactsSettings contactsSettings = new ContactsSettings();
            contactsSettings.setRole(ContactStrings.ROLE_SERVER);
            contactsSettings.setJsonFile(new File(BOOTLOADER_DIR.getAbsoluteFile() + File.separator + "json"));
            ContactsService service = fxBootloader.createService("kekse", contactsSettings);
            MeinNotification notification = new MeinNotification(service.getUuid(), ContactStrings.INTENT_PROPAGATE_NEW_VERSION, "bla", "keks");
            mas.onNotificationFromService(service, notification);
            System.out.println("NotificationFXTest.notification");

        }));
        new RWLock().lockWrite().lockWrite();
    }
}
