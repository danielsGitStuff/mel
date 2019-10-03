package de.mel.auth.service;

import de.mel.Lok;
import de.mel.auth.MelAuthAdmin;
import de.mel.auth.MelNotification;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.MelRequest;
import de.mel.auth.data.NetworkEnvironment;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.Service;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.file.AFile;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.socket.process.reg.IRegisterHandler;
import de.mel.auth.socket.process.reg.IRegisterHandlerListener;
import de.mel.auth.socket.MelValidationProcess;
import de.mel.auth.tools.N;
import de.mel.core.serialize.deserialize.collections.PrimitiveCollectionDeserializerFactory;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mel.core.serialize.serialize.fieldserializer.collections.PrimitiveCollectionSerializerFactory;
import de.mel.drive.FileSyncBootloader;
import de.mel.drive.FileSyncCreateServiceHelper;
import de.mel.drive.bash.BashTools;
import de.mel.sql.RWLock;
import de.mel.sql.deserialize.PairDeserializerFactory;
import de.mel.sql.serialize.PairSerializerFactory;
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
    private MelAuthSettings settings;
    private MelAuthService mas;

    public PerfTransferTest() {

    }

    public static Promise<PerfTransferTest, Void, Void> create() {
        //init
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PrimitiveCollectionSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PrimitiveCollectionDeserializerFactory.getInstance());
        BashTools.init();
        CertificateManager.deleteDirectory(MelBoot.Companion.getDefaultWorkingDir1());

        DeferredObject result = new DeferredObject();
        PerfTransferTest perfTransferTest = new PerfTransferTest();
        MelAuthSettings settings = MelAuthSettings.createDefaultSettings();
        MelBoot boot = new MelBoot(settings, new PowerManager(settings), FileSyncBootloader.class);
        boot.addMelAuthAdmin(new MelAuthAdmin() {
            @Override
            public void onProgress(MelNotification notification, int max, int current, boolean indeterminate) {

            }

            @Override
            public void onCancel(MelNotification notification) {

            }

            @Override
            public void onFinish(MelNotification notification) {

            }

            @Override
            public void start(MelAuthService mas) {
                mas.addRegisterHandler(new IRegisterHandler() {
                    @Override
                    public void acceptCertificate(IRegisterHandlerListener listener, MelRequest request, Certificate myCertificate, Certificate certificate) {
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

                    @Override
                    public void setup(MelAuthAdmin melAuthAdmin) {

                    }
                });
                mas.addRegisteredHandler((melAuthService, registered) -> {
                    melAuthService.getMelServices().forEach(iMelService -> N.r(() -> {
                        Service service = melAuthService.getDatabaseManager().getServiceByUuid(iMelService.getUuid());
                        melAuthService.getDatabaseManager().grant(service.getId().v(), registered.getId().v());
                    }));
                });
                perfTransferTest.mas = mas;
            }

            @Override
            public void onNotificationFromService(IMelService melService, MelNotification notification) {

            }

            @Override
            public void onChanged() {

            }

            @Override
            public void shutDown() {

            }
        });
        N.r(() -> {
            Promise<MelAuthService, Exception, Void> booted = boot.boot();
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
            FileSyncCreateServiceHelper createController = new FileSyncCreateServiceHelper(test.mas);
            createController.createServerService("server", AFile.instance(PerfTransferTest.SOURCE_PATH), 0.1f, 30, false);
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
            Promise<MelValidationProcess, Exception, Void> connected = test.mas.connect("192.168.1.109", 8888, 8889, true);
            connected.done(mvp -> N.r(() -> {
                NetworkEnvironment nve = test.mas.getNetworkEnvironment();
                nve.clear();
                nve.addObserver((o, arg) -> {
                    N.r(() -> {
                        List<ServiceJoinServiceType> services = nve.getServices(mvp.getConnectedId());
                        if (services.size() > 0) {
                            FileSyncCreateServiceHelper createController = new FileSyncCreateServiceHelper(test.mas);
                            FileSyncBootloader.DEV_DRIVE_BOOT_LISTENER = driveService -> {
                                Lok.debug("PerfTransferTest.startTarget.done");
                            };
                            createController.createClientService("client", AFile.instance(PerfTransferTest.TARGET_PATH), mvp.getConnectedId(), services.get(0).getUuid().v(), 0.1f, 30, false);
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
            Promise<MelValidationProcess, Exception, Void> connected = test.mas.connect("192.168.1.109", 8888, 8889, true);
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
