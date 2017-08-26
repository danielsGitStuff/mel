package de.mein.auth.service;


import de.mein.DeferredRunnable;
import de.mein.MeinRunnable;
import de.mein.auth.MeinAuthAdmin;
import de.mein.auth.MeinNotification;
import de.mein.auth.MeinStrings;
import de.mein.auth.broadcast.MeinAuthBrotCaster;
import de.mein.auth.data.ApprovalMatrix;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.access.DatabaseManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.jobs.ConnectJob;
import de.mein.auth.jobs.IsolatedConnectJob;
import de.mein.auth.jobs.NetworkEnvDiscoveryJob;
import de.mein.auth.socket.MeinAuthSocket;
import de.mein.auth.socket.MeinSocket;
import de.mein.auth.socket.ShamefulSelfConnectException;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisteredHandler;
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess;
import de.mein.auth.socket.process.val.MeinServicesPayload;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.sql.SqlQueriesException;
import de.mein.sql.deserialize.PairDeserializerFactory;
import de.mein.sql.serialize.PairSerializerFactory;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by xor on 2/14/16.
 */
public class MeinAuthService {

    private static Logger logger = Logger.getLogger(MeinAuthService.class.getName());
    private final MeinAuthSettings settings;
    private MeinAuthWorker meinAuthWorker;
    protected final CertificateManager certificateManager;
    private final IDBCreatedListener dbCreatedListener;
    private List<MeinAuthAdmin> meinAuthAdmins = new ArrayList<>();
    private final DatabaseManager databaseManager;
    private final File workingDirectory;
    protected List<IRegisterHandler> registerHandlers = new ArrayList<>();
    private List<IRegisteredHandler> registeredHandlers = new ArrayList<>();
    private NetworkEnvironment networkEnvironment = new NetworkEnvironment();
    private Set<MeinSocket> sockets = new HashSet<>();
    private ConnectedEnvironment connectedEnvironment = new ConnectedEnvironment();
    private WaitLock uuidServiceMapSemaphore = new WaitLock();
    private Map<String, MeinService> uuidServiceMap = new ConcurrentHashMap<>();
    private MeinAuthBrotCaster brotCaster;
    private MeinBoot meinBoot;
    private DeferredObject<DeferredRunnable, Exception, Void> startedPromise;


    MeinAuthService(MeinAuthSettings meinAuthSettings, IDBCreatedListener dbCreatedListener) throws Exception {
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.printSerializers();
        this.workingDirectory = meinAuthSettings.getWorkingDirectory();
        this.databaseManager = new DatabaseManager(meinAuthSettings);
        this.certificateManager = new CertificateManager(workingDirectory, databaseManager.getSqlQueries(), 1024);
        this.settings = meinAuthSettings;
        this.dbCreatedListener = dbCreatedListener;
        if (this.databaseManager.hadToInitialize() && this.dbCreatedListener != null)
            this.dbCreatedListener.onDBcreated(this.databaseManager);
        addRegisteredHandler((meinAuthService, registered) -> notifyAdmins());
    }

    public MeinAuthSettings getSettings() {
        return settings;
    }

    public List<Long> getConnectedUserIds() {
        List<Long> ids = connectedEnvironment.getConnectedIds();
        return ids;
    }

    public MeinAuthService addMeinAuthAdmin(MeinAuthAdmin admin) {
        meinAuthAdmins.add(admin);
        admin.start(this);
        return this;
    }

    public void notifyAdmins() {
        for (MeinAuthAdmin admin : meinAuthAdmins) {
            try {
                admin.onChanged();
            } catch (Exception e) {
                System.err.println(getName() + ".notifyAdmins.fail: " + e.getMessage());
            }
        }
    }

    MeinAuthService(MeinAuthSettings meinAuthSettings) throws Exception {
        this(meinAuthSettings, null);
    }

    public MeinAuthService addRegisteredHandler(IRegisteredHandler IRegisteredHandler) {
        this.registeredHandlers.add(IRegisteredHandler);
        return this;
    }

    public File getWorkingDirectory() {
        return workingDirectory;
    }

    public List<IRegisteredHandler> getRegisteredHandlers() {
        return registeredHandlers;
    }


    public MeinAuthService addRegisterHandler(IRegisterHandler registerHandler) {
        this.registerHandlers.add(registerHandler);
        return this;
    }

    public DeferredObject<DeferredRunnable, Exception, Void> prepareStart() {
        N.r(() -> this.meinAuthWorker = new MeinAuthWorker(this, settings));
        startedPromise = meinAuthWorker.getStartedDeferred();
        return startedPromise;
    }


    public void start() {
        execute(meinAuthWorker);
        for (MeinService service : uuidServiceMap.values()) {
            if (service instanceof MeinServiceWorker) {
                ((MeinServiceWorker) service).start();
            }
        }
//        for (MeinAuthAdmin admin : meinAuthAdmins) {
//            admin.start(this);
//        }
    }


    public List<Certificate> getTrustedCertificates() throws SqlQueriesException {
        List<Certificate> certs = certificateManager.getTrustedCertificates();
        return certs;
    }


    public CertificateManager getCertificateManager() {
        return certificateManager;
    }

    public String getName() {
        return settings.getName();
    }

    public Certificate getMyCertificate() throws CertificateEncodingException {
        Certificate certificate = new Certificate();
        certificate.setCertificate(this.certificateManager.getMyX509Certificate().getEncoded());
        certificate.setName(settings.getName());
        return certificate;
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + "." + settings.getName();
    }


    public List<IRegisterHandler> getRegisterHandlers() {
        return registerHandlers;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }


    public Request<MeinServicesPayload> getAllowedServices(Long certificateId) throws JsonSerializationException, IllegalAccessException {
        MeinValidationProcess validationProcess = connectedEnvironment.getValidationProcess(certificateId);
        Request<MeinServicesPayload> promise = validationProcess.request(MeinStrings.SERVICE_NAME, MeinStrings.msg.INTENT_GET_SERVICES, null);
        return promise;
    }


    public IMeinService getMeinService(String serviceUuid) {
        return uuidServiceMap.get(serviceUuid);
    }


    public MeinAuthService registerMeinService(MeinService meinService) throws SqlQueriesException {
        if (meinService.getUuid() == null)
            System.err.println("MeinAuthService.registerMeinService: MeinService.UUID was NULL");
        uuidServiceMapSemaphore.lock();
        uuidServiceMap.put(meinService.getUuid(), meinService);
        if (meinAuthWorker.getStartedDeferred().isResolved()) {
            meinService.onMeinAuthIsUp();
        }
        uuidServiceMapSemaphore.unlock();
        notifyAdmins();
        return this;
    }

    public MeinAuthService unregisterMeinService(Long serviceUuid) {
        uuidServiceMapSemaphore.lock();
        uuidServiceMap.remove(serviceUuid);
        uuidServiceMapSemaphore.unlock();
        notifyAdmins();
        return this;
    }


    public MeinAuthService setName(String name) {
        this.settings.setName(name);
        return this;
    }

    public static void main(String[] args) throws Exception {
        Set<InetAddress> addresses = new HashSet<>();
        InetAddress i1 = InetAddress.getByName("127.0.0.1");
        InetAddress i2 = InetAddress.getByName("127.0.0.1");
        addresses.add(i1);
        addresses.add(i2);
        addresses.forEach(inetAddress -> System.out.println(inetAddress.toString()));
    }


    public void updateCertAddresses(Long remoteCertId, String address, Integer port, Integer portCert) throws SqlQueriesException {
        Certificate c = certificateManager.getTrustedCertificateById(remoteCertId);
        c.setAddress(address).setCertDeliveryPort(portCert).setPort(port);
        certificateManager.updateCertificate(c);
    }


    public Promise<MeinAuthService, Exception, Void> boot() {
        DeferredObject<MeinAuthService, Exception, Void> bootedPromise = new DeferredObject<>();
        DeferredObject<DeferredRunnable, Exception, Void> startedPromise = this.prepareStart();
        start();
        System.out.println("MeinAuthService.boot.trying to connect to everybody");
        startedPromise.done(result -> N.r(() -> {
            for (Certificate certificate : certificateManager.getTrustedCertificates()) {
                Promise<MeinValidationProcess, Exception, Void> connected = connect(certificate.getId().v(), certificate.getAddress().v(), certificate.getPort().v(), certificate.getCertDeliveryPort().v(), false);
                connected.done(mvp -> N.r(() -> {

                })).fail(result1 -> System.err.println("MeinAuthServive.boot.could not connect to: '" + certificate.getName().v() + "' address: " + certificate.getAddress().v()));
            }
        }));
        bootedPromise.resolve(this);
        return bootedPromise;
    }

    public MeinAuthService saveApprovals(ApprovalMatrix approvalMatrix) throws SqlQueriesException {
        databaseManager.saveApprovals(approvalMatrix);
        return this;
    }

    public <T extends MeinIsolatedProcess> DeferredObject<T, Exception, Void> connectToService(Class<T> isolatedServiceClass, Long certId, String remoteServiceUuid, String ownServiceUuid, String address, Integer port, Integer portCert) throws SqlQueriesException, InterruptedException {
        Certificate certificate = certificateManager.getTrustedCertificateById(certId);
        if (address == null)
            address = certificate.getAddress().v();
        if (port == null)
            port = certificate.getPort().v();
        if (portCert == null)
            portCert = certificate.getCertDeliveryPort().v();
        IsolatedConnectJob<T> job = new IsolatedConnectJob<>(certId, address, port, portCert, remoteServiceUuid, ownServiceUuid, isolatedServiceClass);
        meinAuthWorker.addJob(job);
        return job.getPromise();
    }

    public synchronized Promise<MeinValidationProcess, Exception, Void> connect(Long certificateId) throws SqlQueriesException, InterruptedException {
        DeferredObject<MeinValidationProcess, Exception, Void> deferred = new DeferredObject<>();
        MeinValidationProcess mvp;
        Certificate certificate = certificateManager.getTrustedCertificateById(certificateId);
        // check if already connected via id and address
        connectedEnvironment.lock();
        try {
            if (certificateId != null && (mvp = connectedEnvironment.getValidationProcess(certificateId)) != null) {
                deferred.resolve(mvp);
            } else if (certificate != null && (mvp = connectedEnvironment.getValidationProcess(certificate.getAddress().v())) != null) {
                deferred.resolve(mvp);
            } else {
                ConnectJob job = new ConnectJob(certificateId, certificate.getAddress().v(), certificate.getPort().v(), certificate.getCertDeliveryPort().v(), false);
                job.getPromise().done(result -> deferred.resolve(result)).fail(result -> deferred.reject(result));
                meinAuthWorker.addJob(job);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connectedEnvironment.unlock();
        }
        return deferred;
    }

    public Promise<MeinValidationProcess, Exception, Void> connect(Long certificateId, String address, int port, int portCert, boolean regOnUnkown) throws InterruptedException {
        DeferredObject<MeinValidationProcess, Exception, Void> deferred = new DeferredObject<>();
        MeinValidationProcess mvp;
        // check if already connected via id and address
        if (certificateId != null && (mvp = connectedEnvironment.getValidationProcess(certificateId)) != null) {
            deferred.resolve(mvp);
        } else if ((mvp = connectedEnvironment.getValidationProcess(address)) != null) {
            deferred.resolve(mvp);
        } else {
            ConnectJob job = new ConnectJob(certificateId, address, port, portCert, regOnUnkown);
            job.getPromise().done(result -> deferred.resolve((MeinValidationProcess) result)).fail(result -> deferred.reject(result));
            meinAuthWorker.addJob(job);
        }
        return deferred;
    }

    public Set<IMeinService> getMeinServices() {
        uuidServiceMapSemaphore.lock();
        Set<IMeinService> result = new HashSet<>(uuidServiceMap.values());
        uuidServiceMapSemaphore.unlock();
        return result;
    }

    public void setBrotCaster(MeinAuthBrotCaster brotCaster) {
        this.brotCaster = brotCaster;
    }

    public MeinAuthBrotCaster getBrotCaster() {
        return brotCaster;
    }

    private void addToNetworkEnvironment(Long certId, MeinServicesPayload meinServicesPayload) {
        networkEnvironment.add(certId, null);
        for (ServiceJoinServiceType service : meinServicesPayload.getServices()) {
            networkEnvironment.add(certId, service);
        }
    }

    private void connectAndCollect(Map<String, Boolean> checkedAddresses, NetworkEnvironment networkEnvironment, Certificate intendedCertificate) throws IOException, IllegalAccessException, SqlQueriesException, URISyntaxException, ClassNotFoundException, KeyManagementException, BadPaddingException, KeyStoreException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableKeyException, CertificateException, NoSuchPaddingException, JsonSerializationException, IllegalBlockSizeException, InterruptedException {
        String address = MeinAuthSocket.getAddressString(intendedCertificate.getInetAddress(), intendedCertificate.getPort().v());
        N runner = new N(e -> e.printStackTrace());
        if (!checkedAddresses.containsKey(address)) {
            checkedAddresses.put(address, true);
            Promise<MeinValidationProcess, Exception, Void> authenticatedPromise = this.connect(intendedCertificate.getId().v(), intendedCertificate.getAddress().v(), intendedCertificate.getPort().v(), intendedCertificate.getCertDeliveryPort().v(), false);
            authenticatedPromise.done(meinValidationProcess -> {
                runner.runTry(() -> {
                    Request<MeinServicesPayload> servicesPromise = this.getAllowedServices(meinValidationProcess.getPartnerCertificate().getId().v());
                    servicesPromise.done(meinServicesPayload -> {
                        runner.runTry(() -> this.addToNetworkEnvironment(meinValidationProcess.getPartnerCertificate().getId().v(), meinServicesPayload));
                    });
                });
            }).fail(result -> {
                logger.log(Level.SEVERE, "MeinAuthService.connectAndCollect.fail");
            });
        }
    }

    /**
     * this is because Android does not like to do network stuff on GUI threads
     */
    void discoverNetworkEnvironmentImpl() {
        N runner = new N(e -> e.printStackTrace());
        networkEnvironment.clear();
        Map<String, Boolean> checkedAddresses = new ConcurrentHashMap<>();
        runner.runTry(() -> {
            // check active connections
            for (MeinValidationProcess validationProcess : connectedEnvironment.getValidationProcesses()) {
                Long certId = validationProcess.getConnectedId();
                networkEnvironment.add(certId, null);
                checkedAddresses.put(validationProcess.getAddressString(), true);
                Request<MeinServicesPayload> gotServicesPromise = this.getAllowedServices(certId);
                gotServicesPromise.done(meinServicesPayload -> {
                    logger.log(Level.SEVERE, "MeinAuthService.discoverNetworkEnvironment.NOT.IMPLEMENTED.YET");
                    addToNetworkEnvironment(certId, meinServicesPayload);
                });
            }
            // check DB entries
            for (Certificate intendedCertificate : certificateManager.getTrustedCertificates()) {
                connectAndCollect(checkedAddresses, networkEnvironment, intendedCertificate);
            }
            // discover, connect & collect results
            meinAuthWorker.getBrotCaster().setBrotCasterListener((inetAddress, port, portCert) -> {
                runner.runTry(() -> {
                    String address = MeinAuthSocket.getAddressString(inetAddress, port);
                    checkedAddresses.put(address, true);
                    Promise<MeinValidationProcess, Exception, Void> promise = this.connect(null, inetAddress.getHostAddress(), port, portCert, false);
                    promise.done(meinValidationProcess -> {
                        runner.runTry(() -> {
                            networkEnvironment.add(meinValidationProcess.getPartnerCertificate().getId().v(), null);
                            getAllowedServices(meinValidationProcess.getPartnerCertificate().getId().v()).done(meinServicesPayload -> {
                                runner.runTry(() -> {
                                            for (ServiceJoinServiceType service : meinServicesPayload.getServices()) {
                                                networkEnvironment.add(meinValidationProcess.getPartnerCertificate().getId().v(), service);
                                            }
                                        }
                                );
                            });
                        });
                    }).fail(result -> {
                        if (!(result instanceof ShamefulSelfConnectException))
                            networkEnvironment.addUnkown(inetAddress.getHostAddress(), port, portCert);
                    });
                });
            });
            meinAuthWorker.getBrotCaster().discover(settings.getBrotcastPort());
        });
    }

    public MeinAuthService discoverNetworkEnvironment() {
        meinAuthWorker.addJob(new NetworkEnvDiscoveryJob());
        return this;
    }

    public NetworkEnvironment getNetworkEnvironment() {
        return networkEnvironment;
    }


    public void onSocketAuthenticated(MeinValidationProcess validationProcess) {
        connectedEnvironment.lock();
        this.connectedEnvironment.addValidationProcess(validationProcess);
        connectedEnvironment.unlock();
    }


    public void onSocketClosed(MeinAuthSocket meinAuthSocket) {
        if (meinAuthSocket.isValidated()) {
            connectedEnvironment.lock();
            connectedEnvironment.removeValidationProcess((MeinValidationProcess) meinAuthSocket.getProcess());
            connectedEnvironment.unlock();
        }
        sockets.remove(meinAuthSocket);
    }

    public void execute(MeinRunnable runnable) {
        meinBoot.execute(runnable);
    }

    public void setMeinBoot(MeinBoot meinBoot) {
        this.meinBoot = meinBoot;
    }

    public void shutDown() {
        N.r(() -> {
            uuidServiceMapSemaphore.lock();
            for (MeinService service : uuidServiceMap.values()) {
                service.shutDown();
            }
            uuidServiceMapSemaphore.unlock();
            Set<MeinSocket> socks = new HashSet<>(sockets);
            for (MeinSocket socket : socks) {
                socket.shutDown();
            }
            for (MeinAuthAdmin admin : meinAuthAdmins) {
                admin.shutDown();
            }
            meinAuthWorker.shutDown();
            meinBoot.shutDown();
        });
    }

    public void addMeinSocket(MeinSocket meinSocket) {
        sockets.add(meinSocket);
    }

    public MeinBoot getMeinBoot() {
        return meinBoot;
    }

    public MeinServicesPayload getAllowedServicesFor(Long certId) throws SqlQueriesException {
        MeinServicesPayload payload = new MeinServicesPayload();
        List<ServiceJoinServiceType> services = databaseManager.getAllowedServicesJoinTypes(certId);
        //set flag for running Services, then add to result
        for (ServiceJoinServiceType service : services) {
            boolean running = getMeinService(service.getUuid().v()) != null;
            service.setRunning(running);
            payload.addService(service);
        }
        return payload;
    }

    public void onMeinAuthIsUp() {
        for (IMeinService meinService : getMeinServices()) {
            meinService.onMeinAuthIsUp();
        }
    }

    public void addAllMeinAuthAdmin(List<MeinAuthAdmin> meinAuthAdmins) {
        for (MeinAuthAdmin admin : meinAuthAdmins) {
            this.addMeinAuthAdmin(admin);
        }
    }

    public void onNotificationFromService(MeinService meinService, MeinNotification notification) {
        for (MeinAuthAdmin admin : meinAuthAdmins)
            admin.onNotificationFromService(meinService, notification);
    }
}
