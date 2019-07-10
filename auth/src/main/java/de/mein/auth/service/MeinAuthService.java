package de.mein.auth.service;


import de.mein.DeferredRunnable;
import de.mein.Lok;
import de.mein.MeinRunnable;
import de.mein.auth.MeinAuthAdmin;
import de.mein.auth.MeinNotification;
import de.mein.auth.MeinStrings;
import de.mein.auth.broadcast.MeinAuthBrotCaster;
import de.mein.auth.data.ApprovalMatrix;
import de.mein.auth.data.EmptyPayload;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.access.DatabaseManager;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.jobs.AConnectJob;
import de.mein.auth.jobs.ConnectJob;
import de.mein.auth.jobs.IsolatedConnectJob;
import de.mein.auth.jobs.NetworkEnvDiscoveryJob;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.socket.ConnectWorker;
import de.mein.auth.socket.MeinAuthSocket;
import de.mein.auth.socket.MeinSocket;
import de.mein.auth.socket.ShamefulSelfConnectException;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisteredHandler;
import de.mein.auth.socket.process.transfer.MeinIsolatedFileProcess;
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess;
import de.mein.auth.socket.process.val.MeinServicesPayload;
import de.mein.auth.socket.MeinValidationProcess;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.auth.tools.WaitLock;
import de.mein.auth.tools.lock.T;
import de.mein.auth.tools.lock.Transaction;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.sql.SqlQueriesException;
import de.mein.sql.deserialize.PairCollectionDeserializerFactory;
import de.mein.sql.deserialize.PairDeserializerFactory;
import de.mein.sql.serialize.PairCollectionSerializerFactory;
import de.mein.sql.serialize.PairSerializerFactory;
import de.mein.update.Updater;

import org.jdeferred.Promise;
import org.jdeferred.impl.DefaultDeferredManager;
import org.jdeferred.impl.DeferredObject;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Created by xor on 2/14/16.
 */
public class MeinAuthService {

    private final MeinAuthSettings settings;
    private final Updater updater;
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
    private ConnectedEnvironment connectedEnvironment;
    private WaitLock uuidServiceMapSemaphore = new WaitLock();
    private Map<String, MeinService> uuidServiceMap = new HashMap<>();
    private MeinAuthBrotCaster brotCaster;
    private MeinBoot meinBoot;
    private DeferredObject<DeferredRunnable, Exception, Void> startedPromise;
    private PowerManager powerManager;
    private File cacheDir;


    public MeinAuthService(MeinAuthSettings meinAuthSettings, PowerManager powerManager, IDBCreatedListener dbCreatedListener) throws Exception {
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairCollectionSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairCollectionDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.printSerializers();
        this.connectedEnvironment = new ConnectedEnvironment(this);
        this.powerManager = powerManager;
        this.workingDirectory = meinAuthSettings.getWorkingDirectory();
        this.cacheDir = new File(workingDirectory, "auth.cache");
        this.cacheDir.mkdirs();
        this.databaseManager = new DatabaseManager(meinAuthSettings);
        this.certificateManager = new CertificateManager(workingDirectory, databaseManager.getSqlQueries(), 2048);
        try {
            this.certificateManager.maintenance();
            this.databaseManager.maintenance();
        } catch (Exception e) {
            e.printStackTrace();
        }
//        N.r(this.certificateManager::maintenance);
        this.settings = meinAuthSettings;
        this.updater = new Updater(this);
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
                Lok.error(getName() + ".notifyAdmins.fail: " + e.getMessage());
            }
        }
    }

    MeinAuthService(MeinAuthSettings meinAuthSettings, PowerManager powerManager) throws Exception {
        this(meinAuthSettings, powerManager, null);
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

    public void updateProgram() throws UnrecoverableKeyException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException, IOException {
        updater.retrieveUpdate();
    }

    public void start() {
        Lok.debug("start");
        execute(meinAuthWorker);
        for (IMeinService service : uuidServiceMap.values()) {
            if (service instanceof MeinServiceWorker) {
                ((MeinServiceWorker) service).start();
            }
        }
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
        Request<MeinServicesPayload> promise = validationProcess.request(MeinStrings.SERVICE_NAME, new EmptyPayload(MeinStrings.msg.INTENT_GET_SERVICES));
        return promise;
    }


    public MeinService getMeinService(String serviceUuid) {
        uuidServiceMapSemaphore.lock();
        MeinService service = uuidServiceMap.get(serviceUuid);
        uuidServiceMapSemaphore.unlock();
        return service;
    }


    /**
     * services must register using this method if they want to communicate over the network.
     * otherwise they won't be able to receive messages.
     *
     * @param meinService
     * @return
     * @throws SqlQueriesException
     */
    public MeinAuthService registerMeinService(MeinService meinService) throws SqlQueriesException {
        if (meinService.getUuid() == null)
            Lok.error("MeinAuthService.registerMeinService: MeinService.UUID was NULL");
        uuidServiceMapSemaphore.lock();
        uuidServiceMap.put(meinService.getUuid(), meinService);
        if (meinAuthWorker.getStartedDeferred().isResolved()) {
            meinService.onServiceRegistered();
        }
        uuidServiceMapSemaphore.unlock();
        notifyAdmins();
        return this;
    }

    public MeinAuthService unregisterMeinService(String serviceUuid) {
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

    public void updateCertAddresses(Long remoteCertId, String address, Integer port, Integer portCert) throws SqlQueriesException {
        Certificate c = certificateManager.getTrustedCertificateById(remoteCertId);
        c.setAddress(address).setCertDeliveryPort(portCert).setPort(port);
        certificateManager.updateCertificate(c);
    }


    public Promise<MeinAuthService, Exception, Void> boot() {
        DeferredObject<MeinAuthService, Exception, Void> bootedPromise = new DeferredObject<>();
        DeferredObject<DeferredRunnable, Exception, Void> startedPromise = this.prepareStart();
        start();
        Lok.debug("MeinAuthService.boot.trying to connect to everybody");
        startedPromise.done(result -> N.r(() -> {
            for (Certificate certificate : certificateManager.getTrustedCertificates()) {
                Promise<MeinValidationProcess, Exception, Void> connected = connect(certificate.getId().v());
                connected.done(mvp -> N.r(() -> {

                })).fail(result1 -> Lok.error("MeinAuthServive.boot.could not connect to: '" + certificate.getName().v() + "' address: " + certificate.getAddress().v()));
            }
        }));
        bootedPromise.resolve(this);
        return bootedPromise;
    }

    public MeinAuthService saveApprovals(ApprovalMatrix approvalMatrix) throws SqlQueriesException {
        databaseManager.saveApprovals(approvalMatrix);
        return this;
    }

    <T extends MeinIsolatedProcess> DeferredObject<T, Exception, Void> connectToService(Class<T> isolatedServiceClass, Long certId, String remoteServiceUuid, String ownServiceUuid, String address, Integer port, Integer portCert) throws SqlQueriesException, InterruptedException {
        Certificate certificate = certificateManager.getTrustedCertificateById(certId);
        if (address == null)
            address = certificate.getAddress().v();
        if (port == null)
            port = certificate.getPort().v();
        if (portCert == null)
            portCert = certificate.getCertDeliveryPort().v();
        IsolatedConnectJob<T> job = new IsolatedConnectJob<>(certId, address, port, portCert, remoteServiceUuid, ownServiceUuid, isolatedServiceClass);
        execute(new ConnectWorker(this, job));
        return job.getPromise();
    }

    public synchronized Promise<MeinValidationProcess, Exception, Void> connect(Long certificateId) throws SqlQueriesException, InterruptedException {
        DeferredObject<MeinValidationProcess, Exception, Void> deferred = new DeferredObject<>();
        if (certificateId == null) {
            return deferred.reject(new Exception("certificateid was null"));
        }
        Certificate certificate = certificateManager.getTrustedCertificateById(certificateId);
        if (certificate == null) {
            return deferred.reject(new Exception("no certificate found for id: " + certificate));
        }
        return connectedEnvironment.connect(certificate);

//        MeinValidationProcess mvp;
//        Certificate certificate = certificateManager.getTrustedCertificateById(certificateId);
//        if (certificate == null) {
//            Lok.error("No Certificate found for id: " + certificateId);
//            deferred.reject(new Exception("certificate not found"));
//            return deferred;
//        }
//        // check if already connected via id and address
//        Transaction transaction = null;
//        try {
//            transaction = T.lockingTransaction(T.read(connectedEnvironment));
//            Promise<MeinValidationProcess, Exception, Void> def = connectedEnvironment.currentlyConnecting(certificateId);
//            if (def != null) {
//                return def;
//            }
//            if (certificateId != null && (mvp = connectedEnvironment.getValidationProcess(certificateId)) != null) {
//                deferred.resolve(mvp);
//            } else if (certificate != null && (mvp = connectedEnvironment.getValidationProcess(certificate.getAddress().v(), certificate.getPort().v())) != null) {
//                deferred.resolve(mvp);
//            } else {
//                ConnectJob job = new ConnectJob(certificateId, certificate.getAddress().v(), certificate.getPort().v(), certificate.getCertDeliveryPort().v(), false);
//                connectedEnvironment.currentlyConnecting(certificateId, deferred);
//                job.getPromise().done(result -> {
//                    // use a new transaction, because we want connect in parallel.
//                    T.lockingTransaction(connectedEnvironment)
//                            .run(() -> connectedEnvironment.removeCurrentlyConnecting(certificateId))
//                            .end();
//                    deferred.resolve(result);
//                }).fail(result -> {
//                    T.lockingTransaction(connectedEnvironment)
//                            .run(() -> connectedEnvironment.removeCurrentlyConnecting(certificateId))
//                            .end();
//                    deferred.reject(result);
//                });
//                execute(new ConnectWorker(this, job));
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            if (transaction != null) {
//                transaction.end();
//            }
//        }
//        return deferred;
    }


    Promise<MeinValidationProcess, Exception, Void> connect(String address, int port, int portCert, boolean regOnUnkown) throws InterruptedException {
        return connectedEnvironment.connect(address, port, portCert, regOnUnkown);
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
            Promise<MeinValidationProcess, Exception, Void> authenticatedPromise = this.connect(intendedCertificate.getAddress().v(), intendedCertificate.getPort().v(), intendedCertificate.getCertDeliveryPort().v(), false);
            authenticatedPromise.done(meinValidationProcess -> {
                runner.runTry(() -> {
                    Request<MeinServicesPayload> servicesPromise = this.getAllowedServices(meinValidationProcess.getPartnerCertificate().getId().v());
                    servicesPromise.done(meinServicesPayload -> {
                        runner.runTry(() -> this.addToNetworkEnvironment(meinValidationProcess.getPartnerCertificate().getId().v(), meinServicesPayload));
                    });
                });
            }).fail(result -> {
                Lok.error("MeinAuthService.connectAndCollect.fail() for: " + address + "");
            });
        }
    }

    /**
     * this is because Android does not like to do network stuff on GUI threads
     */
    void discoverNetworkEnvironmentImpl() {
        Lok.debug("discovering network");
        N runner = new N(Throwable::printStackTrace);
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
                    Lok.error("MeinAuthService.discoverNetworkEnvironment.NOT.IMPLEMENTED.YET");
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
                    Promise<MeinValidationProcess, Exception, Void> promise = this.connect(inetAddress.getHostAddress(), port, portCert, false);
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
        Lok.debug("creating discover job");
        meinAuthWorker.addJob(new NetworkEnvDiscoveryJob());
        return this;
    }

    public NetworkEnvironment getNetworkEnvironment() {
        return networkEnvironment;
    }


    public void onSocketAuthenticated(MeinValidationProcess validationProcess) {
//        Lok.debug("debug authenticated 1");
        Transaction transaction = null;
        try {
            transaction = T.lockingTransaction(connectedEnvironment);
        } finally {
//            Lok.debug("debug authenticated 2");
            if (transaction != null) {
                connectedEnvironment.addValidationProcess(validationProcess);
                transaction.end();
//                Lok.debug("debug authenticated 3");
            }
        }
        T.lockingRun(() -> this.connectedEnvironment.addValidationProcess(validationProcess), connectedEnvironment);
    }

//    private static AtomicLong closeCount = new AtomicLong(0L);

    public void onSocketClosed(MeinAuthSocket meinAuthSocket) {
//        Eva.trace();
//        final long debugCount = closeCount.getAndIncrement();
//        Lok.debug("debug close 1 /" + debugCount);
        Transaction transaction = null;
        try {
            transaction = T.lockingTransaction(connectedEnvironment);
        } finally {
//            Lok.debug("debug close 2 /" + debugCount);
            // we want to ensure this code block can alway run and is not interrupted
            sockets.remove(meinAuthSocket);
            // find the socket in the connected environment and remove it
            AConnectJob connectJob = meinAuthSocket.getConnectJob();
//            Lok.debug("debug close 2.1 /" + debugCount);
            if (meinAuthSocket.isValidated() && meinAuthSocket.getProcess() instanceof MeinValidationProcess) {
//                Lok.debug("debug close 2.2 /" + debugCount);
                connectedEnvironment.removeValidationProcess((MeinValidationProcess) meinAuthSocket.getProcess());
//                Lok.debug("debug close 2.3 /" + debugCount);
            } else if (meinAuthSocket.getProcess() instanceof MeinIsolatedFileProcess) {
//            meinAuthSocket.getProcess().stop();
                Lok.debug("continue here");
//                Lok.debug("debug close 2.4 /" + debugCount);
            } else if (connectJob != null) {
//                Lok.debug("debug close 2.5 /" + debugCount);
                if (connectJob.getCertificateId() != null) {
//                    Lok.debug("debug close 2.6 /" + debugCount);
                    N.r(() -> connectedEnvironment.removeCurrentlyConnecting(meinAuthSocket.getConnectJob().getCertificateId()));
//                    Lok.debug("debug close 2.7 /" + debugCount);
                } else if (connectJob.getAddress() != null) {
//                    Lok.debug("debug close 2.8 /" + debugCount);
                    N.r(() -> connectedEnvironment.removeCurrentlyConnecting(connectJob.getAddress(), connectJob.getPort(), connectJob.getPortCert()));
//                    Lok.debug("debug close 2.9 /" + debugCount);
                }
                //todo debug
//                if (debugCount == 2L)
//                    Lok.debug("debug");
//                Lok.debug("debug close 2.10 /" + debugCount);
                transaction.end();
                N.oneLine(() -> {
                    if (connectJob.getPromise().isPending()) {
                        connectJob.getPromise().reject(new Exception("connection closed"));
                    }
                });
//                Lok.debug("debug close 2.11 /" + debugCount);
//            connectJob.getPromise().reject(null);
            }

            if (transaction != null) {
                transaction.end();
//                Lok.debug("debug close 3/" + debugCount);
            }
//            Lok.debug("debug close 4/" + debugCount);
        }


    }

    public void execute(MeinRunnable runnable) {
        meinBoot.execute(runnable);
    }

    public void setMeinBoot(MeinBoot meinBoot) {
        this.meinBoot = meinBoot;
    }

    /**
     * shuts down all running services and connections.
     */
    public void shutDown() {
        uuidServiceMapSemaphore.lock();
        for (IMeinService service : uuidServiceMap.values()) {
            if (service instanceof MeinService) {
                N.r(((MeinService) service)::shutDown);
            }
        }
        uuidServiceMapSemaphore.unlock();
//        connectedEnvironment.shutDown();
        Set<MeinSocket> socks = new HashSet<>(sockets);
        for (MeinSocket socket : socks) {
            socket.shutDown();
        }
        for (MeinAuthAdmin admin : meinAuthAdmins) {
            admin.shutDown();
        }
        meinAuthWorker.shutDown();
        N.r(meinBoot::shutDown);
        databaseManager.shutDown();
    }

    public void suspend() {
        uuidServiceMapSemaphore.lock();
        for (IMeinService service : uuidServiceMap.values()) {
            if (service instanceof MeinService) {
                N.r(((MeinService) service)::stop);
            }
        }
        uuidServiceMapSemaphore.unlock();
//        connectedEnvironment.shutDown();
        Set<MeinSocket> socks = new HashSet<>(sockets);
        Lok.debug("closing no of sockets: " + socks.size());
        for (MeinSocket socket : socks) {
            socket.shutDown();
        }
        meinAuthWorker.shutDown();

    }

    public void resume() {
        Lok.debug("resume");
        meinBoot.onHeavyWorkAllowed();
        uuidServiceMapSemaphore.lock();
        List<DeferredObject> servicesStarted = new ArrayList<>();
        for (IMeinService service : uuidServiceMap.values()) {
            if (service instanceof MeinService) {
                MeinService meinService = (MeinService) service;
                DeferredObject<DeferredRunnable, Exception, Void> startedPromise = new DeferredObject<>();
                meinService.setStartedPromise(startedPromise);
//                servicesStarted.add(startedPromise);
                meinService.resume();
                execute(meinService);
            }
        }
        uuidServiceMapSemaphore.unlock();
        // reconnect everything after resume
        meinAuthWorker.addJob(new NetworkEnvDiscoveryJob());
        DeferredObject[] arr = N.arr.fromCollection(servicesStarted, N.converter(DeferredObject.class, element -> element));
        if (arr.length > 0) {
            new DefaultDeferredManager().when(arr).done(result -> execute(meinAuthWorker))
                    .fail(result -> Lok.error("could not resume!"));
        } else {
            execute(meinAuthWorker);
        }
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
            meinService.onServiceRegistered();
        }
    }

    public void addAllMeinAuthAdmin(List<MeinAuthAdmin> meinAuthAdmins) {
        for (MeinAuthAdmin admin : meinAuthAdmins) {
            this.addMeinAuthAdmin(admin);
        }
    }

    public String getCompleteNotificationText(MeinNotification notification) {
        IMeinService meinService = getMeinService(notification.getServiceUuid());
        final String serviceName = databaseManager.getServiceName(meinService);
        final String serviceTypeName = databaseManager.getServiceTypeName(meinService);
        return "from " + serviceTypeName + "/" + serviceName + ": " + notification.getText();
    }

    public void onNotificationFromService(IMeinService meinService, MeinNotification notification) {
        for (MeinAuthAdmin admin : meinAuthAdmins) {
            notification.addProgressListener(admin);
            admin.onNotificationFromService(meinService, notification);
        }
    }

    public PowerManager getPowerManager() {
        return powerManager;
    }

    /**
     * services changed names or something {@link MeinAuthService displays}
     */
    public void onServicesChanged() {
        for (MeinAuthAdmin admin : meinAuthAdmins) {
            admin.onChanged();
        }
    }

    public Updater getUpdater() {
        return updater;
    }

    public void shutDownCommunications() {
        this.meinAuthWorker.shutDown();
    }

    public DeferredObject<DeferredRunnable, Exception, Void> startUpCommunications() {
        DeferredObject<DeferredRunnable, Exception, Void> deferred = new DeferredObject<>();
        if (meinAuthWorker.isStopped()) {
            // restart
            meinAuthWorker.setStartedPromise(deferred);
            this.execute(meinAuthWorker);
        } else {
            // it is still running
            deferred.resolve(meinAuthWorker);
        }
        return deferred;
    }

    public File getCacheDir() {
        return cacheDir;
    }

    public boolean isConnectedTo(Long certId) {
        AtomicBoolean connected = new AtomicBoolean(false);
        return N.result(() -> {
            Transaction transaction = null;
            try {
                transaction = T.lockingTransaction(T.read(connectedEnvironment));
                if (connectedEnvironment.getValidationProcess(certId) != null)
                    connected.set(true);
            } finally {
                if (transaction != null)
                    transaction.end();
            }
            return connected.get();
        }, false);
    }
}
