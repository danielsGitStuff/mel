package de.mein;

import de.mein.auth.MeinAuthAdmin;
import de.mein.auth.MeinNotification;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.tools.N;
import de.mein.core.serialize.deserialize.collections.PrimitiveCollectionDeserializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.core.serialize.serialize.fieldserializer.collections.PrimitiveCollectionSerializerFactory;
import de.mein.drive.DriveBootLoader;
import de.mein.drive.DriveCreateController;
import de.mein.drive.bash.BashTools;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.sql.RWLock;
import de.mein.sql.deserialize.PairDeserializerFactory;
import de.mein.sql.serialize.PairSerializerFactory;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
import org.junit.Test;

import java.io.File;
import java.util.List;

/**
 * run on two different machines or limit the connection speed somehow and adjust code accordingly.
 * start source first.
 */
public class PerfTransferTest {

    private static final String SOURCE_PATH = "drivesource.test";
    private static final String TARGET_PATH = "drivetarget.test";
    private MeinAuthSettings settings;
    private MeinAuthService mas;

    public PerfTransferTest() {

    }

    public static Promise<PerfTransferTest, Void, Void> create() {
        //init
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PrimitiveCollectionSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PrimitiveCollectionDeserializerFactory.getInstance());
        BashTools.init();
        CertificateManager.deleteDirectory(MeinBoot.defaultWorkingDir1);

        DeferredObject result = new DeferredObject();
        PerfTransferTest perfTransferTest = new PerfTransferTest();
        MeinAuthSettings settings = MeinAuthSettings.createDefaultSettings();
        MeinBoot boot = new MeinBoot(settings, new PowerManager(settings), DriveBootLoader.class);
        boot.addMeinAuthAdmin(new MeinAuthAdmin() {
            @Override
            public void start(MeinAuthService mas) {
                mas.addRegisterHandler(new IRegisterHandler() {
                    @Override
                    public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {
                        listener.onCertificateAccepted(request, certificate);
                    }

                    @Override
                    public void onRegistrationCompleted(Certificate partnerCertificate) {

                    }

                    @Override
                    public void onRemoteRejected(Certificate partnerCertificate) {

                    }

                    @Override
                    public void onLocallyRejected(Certificate partnerCertificate) {

                    }

                    @Override
                    public void onRemoteAccepted(Certificate partnerCertificate) {

                    }

                    @Override
                    public void onLocallyAccepted(Certificate partnerCertificate) {

                    }
                });
                perfTransferTest.mas = mas;
            }

            @Override
            public void onNotificationFromService(IMeinService meinService, MeinNotification notification) {

            }

            @Override
            public void onChanged() {

            }

            @Override
            public void shutDown() {

            }
        });
        N.r(() ->{
            Promise<MeinAuthService, Exception, Void> booted = boot.boot();
            booted.done(result1 -> N.r(() -> {
                result.resolve(perfTransferTest);

            }));
        });
        return result;
    }

    @Test
    public void startSource() {
        CertificateManager.deleteDirectory(new File(PerfTransferTest.SOURCE_PATH));
        RWLock lock = new RWLock();
        Promise<PerfTransferTest, Void, Void> promise = create();
        promise.done(test -> N.r(() -> {
            DriveCreateController createController = new DriveCreateController(test.mas);
            createController.createDriveServerService("server",PerfTransferTest.SOURCE_PATH,0.1f,30);
            System.out.println("PerfTransferTest.startSource.done");
        }));
        lock.lockWrite().lockWrite();
    }

    @Test
    public void startTarget() {
        CertificateManager.deleteDirectory(new File(PerfTransferTest.TARGET_PATH));
        RWLock lock = new RWLock();
        Promise<PerfTransferTest, Void, Void> promise = create();
        promise.done(test -> N.r(() -> {
            System.out.println("PerfTransferTest.startTarget.connecting");
            Promise<MeinValidationProcess, Exception, Void> connected = test.mas.connect("192.168.1.109", 8888, 8889, true);
            connected.done(mvp -> N.r(() -> {
                mas.getNetworkEnvironment().clear();
                List<ServiceJoinServiceType> services = mas.getNetworkEnvironment().getServices(mvp.getConnectedId());
                DriveCreateController createController = new DriveCreateController(test.mas);
                Promise<MeinDriveClientService, Exception, Void> allDone  = createController.createDriveClientService("client",PerfTransferTest.TARGET_PATH,mvp.getConnectedId(),services.get(0).getUuid().v(),0.1f,30);
                allDone.done(meinDriveClientService -> N.r(() -> {
                    System.out.println("PerfTransferTest.startTarget().done");
                }));
            }));
        }));
        lock.lockWrite().lockWrite();

    }
}
