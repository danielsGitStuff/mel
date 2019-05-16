package de.mein;

import de.mein.auth.MeinAuthAdmin;
import de.mein.auth.MeinNotification;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.file.AFile;
import de.mein.auth.service.IMeinService;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.service.MeinBoot;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;
import de.mein.auth.socket.MeinValidationProcess;
import de.mein.auth.tools.N;
import de.mein.core.serialize.deserialize.collections.PrimitiveCollectionDeserializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.core.serialize.serialize.fieldserializer.collections.PrimitiveCollectionSerializerFactory;
import de.mein.drive.DriveBootloader;
import de.mein.drive.DriveCreateController;
import de.mein.drive.bash.BashTools;
import de.mein.sql.RWLock;
import de.mein.sql.deserialize.PairDeserializerFactory;
import de.mein.sql.serialize.PairSerializerFactory;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Random;

/**
 * run on two different machines or limit the connection speed somehow and adjust code accordingly.
 * start source first.
 */
@SuppressWarnings("ALL")
public class PerfTransferTest {

    private static final String SOURCE_PATH = new File("drivesource.test").getAbsolutePath();
    private static final String TARGET_PATH = new File("drivetarget.test").getAbsolutePath();
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
        CertificateManager.deleteDirectory(MeinBoot.Companion.getDefaultWorkingDir1());

        DeferredObject result = new DeferredObject();
        PerfTransferTest perfTransferTest = new PerfTransferTest();
        MeinAuthSettings settings = MeinAuthSettings.createDefaultSettings();
        MeinBoot boot = new MeinBoot(settings, new PowerManager(settings), DriveBootloader.class);
        boot.addMeinAuthAdmin(new MeinAuthAdmin() {
            @Override
            public void onProgress(MeinNotification notification, int max, int current, boolean indeterminate) {

            }

            @Override
            public void onCancel(MeinNotification notification) {

            }

            @Override
            public void onFinish(MeinNotification notification) {

            }

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
                mas.addRegisteredHandler((meinAuthService, registered) -> {
                    meinAuthService.getMeinServices().forEach(iMeinService -> N.r(() -> {
                        Service service = meinAuthService.getDatabaseManager().getServiceByUuid(iMeinService.getUuid());
                        meinAuthService.getDatabaseManager().grant(service.getId().v(), registered.getId().v());
                    }));
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
        N.r(() -> {
            Promise<MeinAuthService, Exception, Void> booted = boot.boot();
            booted.done(result1 -> N.r(() -> {
                result.resolve(perfTransferTest);

            }));
        });
        return result;
    }

    //    @Test
    public void startSource() {
        // put a really big file in this folder to test sync speed
        //CertificateManager.deleteDirectory(new File(PerfTransferTest.SOURCE_PATH));
        RWLock lock = new RWLock();
        Promise<PerfTransferTest, Void, Void> promise = create();
        promise.done(test -> N.r(() -> {
            DriveCreateController createController = new DriveCreateController(test.mas);
            createController.createDriveServerService("server", AFile.instance(PerfTransferTest.SOURCE_PATH), 0.1f, 30);
            Lok.debug("PerfTransferTest.startSource.done");
        }));
        lock.lockWrite().lockWrite();
    }

    //    @Test
    public void startTarget() {
        CertificateManager.deleteDirectory(new File(PerfTransferTest.TARGET_PATH));
        RWLock lock = new RWLock();
        Promise<PerfTransferTest, Void, Void> promise = create();
        promise.done(test -> N.r(() -> {
            Lok.debug("PerfTransferTest.startTarget.connecting");
            Promise<MeinValidationProcess, Exception, Void> connected = test.mas.connect("192.168.1.109", 8888, 8889, true);
            connected.done(mvp -> N.r(() -> {
                NetworkEnvironment nve = test.mas.getNetworkEnvironment();
                nve.clear();
                nve.addObserver((o, arg) -> {
                    N.r(() -> {
                        List<ServiceJoinServiceType> services = nve.getServices(mvp.getConnectedId());
                        if (services.size() > 0) {
                            DriveCreateController createController = new DriveCreateController(test.mas);
                            DriveBootloader.DEV_DRIVE_BOOT_LISTENER = driveService -> {
                                Lok.debug("PerfTransferTest.startTarget.done");
                            };
                            createController.createDriveClientService("client", AFile.instance(PerfTransferTest.TARGET_PATH), mvp.getConnectedId(), services.get(0).getUuid().v(), 0.1f, 30);
                        }
                    });
                });
                test.mas.discoverNetworkEnvironment();
            }));
        }));
        lock.lockWrite().lockWrite();

    }

    //    @Test
    public void netServer() throws Exception {
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(8888));
        while (true) {
            Socket socket = serverSocket.accept();
            N.r(() -> {
                OutputStream out = socket.getOutputStream();
                Random random = new Random(1);
                while (true) {
                    byte[] bytes = new byte[1024 * 512];
                    random.nextBytes(bytes);
                    out.write(bytes);
                }
            });
        }
    }

    //    @Test
    public void netServerEncrypted() throws Exception {
        Promise<PerfTransferTest, Void, Void> started = create();
        started.done(test -> N.r(() -> {
            Lok.debug("PerfTransferTest.netServerEncrypted() starting server socket");
            ServerSocket serverSocket = test.mas.getCertificateManager().createServerSocket();
            serverSocket.bind(new InetSocketAddress(9999));
            new Thread(() -> {
                N.r(() -> {
                    while (true) {
                        Lok.debug("PerfTransferTest.netServerEncrypted. waiting for connection");
                        Socket socket = serverSocket.accept();
                        Lok.debug("PerfTransferTest.netServerEncrypted. got connection");
                        N.r(() -> {
                            OutputStream out = socket.getOutputStream();
                            Random random = new Random(1);
                            while (true) {
                                byte[] bytes = new byte[1024 * 512];
                                random.nextBytes(bytes);
                                out.write(bytes);
                            }
                        });
                    }
                });
            }).start();


        }));
        new RWLock().lockWrite().lockWrite();
    }

    //    @Test
    public void netClient() throws Exception {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("192.168.1.109", 8888));
        InputStream in = socket.getInputStream();
        while (true) {
            byte[] bytes = new byte[1024 * 512];
            in.read(bytes);
        }
    }

    //    @Test
    public void netClientEncrypted() throws Exception {
        Promise<PerfTransferTest, Void, Void> started = create();
        started.done(test -> N.r(() -> {
            Promise<MeinValidationProcess, Exception, Void> connected = test.mas.connect("192.168.1.109", 8888, 8889, true);
            connected.done(mvp -> N.r(() -> {
                Socket socket = test.mas.getCertificateManager().createSocket();
                socket.connect(new InetSocketAddress("192.168.1.109", 9999));
                InputStream in = socket.getInputStream();
                while (true) {
                    byte[] bytes = new byte[1024 * 512];
                    in.read(bytes);
                }
            }));
        }));
        new RWLock().lockWrite().lockWrite();
    }
}
