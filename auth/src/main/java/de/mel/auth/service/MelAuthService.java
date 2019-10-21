package de.mel.auth.service;


import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.MelRunnable;
import de.mel.auth.MelAuthAdmin;
import de.mel.auth.MelNotification;
import de.mel.auth.MelStrings;
import de.mel.auth.broadcast.MelAuthBrotCaster;
import de.mel.auth.data.ApprovalMatrix;
import de.mel.auth.data.EmptyPayload;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.NetworkEnvironment;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.data.access.DatabaseManager;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.Service;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.data.db.ServiceType;
import de.mel.auth.jobs.IsolatedConnectJob;
import de.mel.auth.jobs.NetworkEnvDiscoveryJob;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.socket.ConnectWorker;
import de.mel.auth.socket.MelAuthSocket;
import de.mel.auth.socket.MelSocket;
import de.mel.auth.socket.ShamefulSelfConnectException;
import de.mel.auth.socket.process.reg.IRegisterHandler;
import de.mel.auth.socket.process.reg.IRegisteredHandler;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
import de.mel.auth.socket.process.val.MelServicesPayload;
import de.mel.auth.socket.MelValidationProcess;
import de.mel.auth.socket.process.val.Request;
import de.mel.auth.tools.N;
import de.mel.auth.tools.ShutDownDeferredManager;
import de.mel.auth.tools.WaitLock;
import de.mel.auth.tools.lock.P;
import de.mel.auth.tools.lock.Warden;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mel.sql.SqlQueriesException;
import de.mel.sql.deserialize.PairCollectionDeserializerFactory;
import de.mel.sql.deserialize.PairDeserializerFactory;
import de.mel.sql.serialize.PairCollectionSerializerFactory;
import de.mel.sql.serialize.PairSerializerFactory;
import de.mel.update.Updater;

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
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Created by xor on 2/14/16.
 */
public class MelAuthService {

    private final MelAuthSettings settings;
    private final Updater updater;
    private MelAuthWorker melAuthWorker;
    protected final CertificateManager certificateManager;
    private List<MelAuthAdmin> melAuthAdmins = new ArrayList<>();
    private final DatabaseManager databaseManager;
    private final File workingDirectory;
    protected List<IRegisterHandler> registerHandlers = new ArrayList<>();
    private List<IRegisteredHandler> registeredHandlers = new ArrayList<>();
    private NetworkEnvironment networkEnvironment = new NetworkEnvironment();
    private Set<MelSocket> sockets = new HashSet<>();
    private ConnectedEnvironment connectedEnvironment;
    private WaitLock uuidServiceMapSemaphore = new WaitLock();
    private Map<String, MelService> uuidServiceMap = new HashMap<>();
    private MelAuthBrotCaster brotCaster;
    private MelBoot melBoot;
    private DeferredObject<DeferredRunnable, Exception, Void> startedPromise;
    private PowerManager powerManager;
    private File cacheDir;


    public MelAuthService(MelAuthSettings melAuthSettings, PowerManager powerManager) throws Exception {
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairCollectionSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairCollectionDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.printSerializers();
        this.connectedEnvironment = new ConnectedEnvironment(this);
        this.powerManager = powerManager;
        this.workingDirectory = melAuthSettings.getWorkingDirectory();
        this.cacheDir = new File(workingDirectory, "auth.cache");
        this.cacheDir.mkdirs();
        this.databaseManager = new DatabaseManager(melAuthSettings);
        this.certificateManager = new CertificateManager(workingDirectory, databaseManager.getSqlQueries(), 2048);
        try {
            this.certificateManager.maintenance();
            this.databaseManager.maintenance();
        } catch (Exception e) {
            e.printStackTrace();
        }
//        N.r(this.certificateManager::maintenance);
        this.settings = melAuthSettings;
        this.settings.save();
        this.updater = new Updater(this);

        addRegisteredHandler((melAuthService, registered) -> notifyAdmins());
    }


    public MelAuthSettings getSettings() {
        return settings;
    }

    public List<Long> getConnectedUserIds() {
        List<Long> ids = connectedEnvironment.getConnectedIds();
        return ids;
    }

    public MelAuthService addMelAuthAdmin(MelAuthAdmin admin) {
        melAuthAdmins.add(admin);
        admin.start(this);
        return this;
    }

    public void notifyAdmins() {
        for (MelAuthAdmin admin : melAuthAdmins) {
            try {
                admin.onChanged();
            } catch (Exception e) {
                Lok.error(getName() + ".notifyAdmins.fail: " + e.getMessage());
            }
        }
    }

    public MelAuthService addRegisteredHandler(IRegisteredHandler IRegisteredHandler) {
        this.registeredHandlers.add(IRegisteredHandler);
        return this;
    }

    public File getWorkingDirectory() {
        return workingDirectory;
    }

    public List<IRegisteredHandler> getRegisteredHandlers() {
        return registeredHandlers;
    }


    public MelAuthService addRegisterHandler(IRegisterHandler registerHandler) {
        this.registerHandlers.add(registerHandler);
        return this;
    }

    public DeferredObject<DeferredRunnable, Exception, Void> prepareStart() {
        N.r(() -> this.melAuthWorker = new MelAuthWorker(this, settings));
        startedPromise = melAuthWorker.getStartedDeferred();
        return startedPromise;
    }

    public void updateProgram() throws UnrecoverableKeyException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException, IOException {
        updater.searchUpdate();
    }

    public void start() {
        Lok.debug("start");
        execute(melAuthWorker);
        for (IMelService service : uuidServiceMap.values()) {
            if (service instanceof MelServiceWorker) {
                ((MelServiceWorker) service).start();
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


    public Request<MelServicesPayload> getAllowedServices(Long certificateId) throws JsonSerializationException, IllegalAccessException {
        MelValidationProcess validationProcess = connectedEnvironment.getValidationProcess(certificateId);
        Request<MelServicesPayload> promise = validationProcess.request(MelStrings.SERVICE_NAME, new EmptyPayload(MelStrings.msg.INTENT_GET_SERVICES));
        return promise;
    }


    public MelService getMelService(String serviceUuid) {
        uuidServiceMapSemaphore.lock();
        MelService service = uuidServiceMap.get(serviceUuid);
        uuidServiceMapSemaphore.unlock();
        return service;
    }


    /**
     * services must register using this method if they want to communicate over the network.
     * otherwise they won't be able to receive messages.
     *
     * @param melService
     * @return
     * @throws SqlQueriesException
     */
    public MelAuthService registerMelService(MelService melService) throws SqlQueriesException {
        if (melService.getUuid() == null)
            Lok.error("MelAuthService.registerMelService: MelService.UUID was NULL");
        uuidServiceMapSemaphore.lock();
        uuidServiceMap.put(melService.getUuid(), melService);
        if (melAuthWorker.getStartedDeferred().isResolved()) {
            melService.onServiceRegistered();
        }
        uuidServiceMapSemaphore.unlock();
        notifyAdmins();
        return this;
    }

    public MelAuthService unregisterMelService(String serviceUuid) {
        uuidServiceMapSemaphore.lock();
        uuidServiceMap.remove(serviceUuid);
        uuidServiceMapSemaphore.unlock();
        notifyAdmins();
        return this;
    }


    public MelAuthService setName(String name) {
        this.settings.setName(name);
        return this;
    }

    public void updateCertAddresses(Long remoteCertId, String address, Integer port, Integer portCert) throws SqlQueriesException {
        Certificate c = certificateManager.getTrustedCertificateById(remoteCertId);
        c.setAddress(address).setCertDeliveryPort(portCert).setPort(port);
        certificateManager.updateCertificate(c);
    }


    public Promise<MelAuthService, Exception, Void> boot() {
        DeferredObject<MelAuthService, Exception, Void> bootedPromise = new DeferredObject<>();
        DeferredObject<DeferredRunnable, Exception, Void> startedPromise = this.prepareStart();
        start();
        Lok.debug("MelAuthService.boot.trying to connect to everybody");
        startedPromise.done(result -> N.r(() -> {
            for (Certificate certificate : certificateManager.getTrustedCertificates()) {
                Promise<MelValidationProcess, Exception, Void> connected = connect(certificate.getId().v());
                connected.done(mvp -> N.r(() -> {

                })).fail(result1 -> Lok.error("MelAuthServive.boot.could not connect to: '" + certificate.getName().v() + "' address: " + certificate.getAddress().v()));
            }
        }));
        bootedPromise.resolve(this);
        return bootedPromise;
    }

    public MelAuthService saveApprovals(ApprovalMatrix approvalMatrix) throws SqlQueriesException {
        databaseManager.saveApprovals(approvalMatrix);
        return this;
    }

    <T extends MelIsolatedProcess> DeferredObject<T, Exception, Void> connectToService(Class<T> isolatedServiceClass, Long certId, String remoteServiceUuid, String ownServiceUuid, String address, Integer port, Integer portCert) throws SqlQueriesException, InterruptedException {
        Certificate certificate = certificateManager.getTrustedCertificateById(certId);
        if (address == null)
            address = certificate.getAddress().v();
        if (port == null)
            port = certificate.getPort().v();
        if (portCert == null)
            portCert = certificate.getCertDeliveryPort().v();
        IsolatedConnectJob<T> job = new IsolatedConnectJob<>(certId, address, port, portCert, remoteServiceUuid, ownServiceUuid, isolatedServiceClass);
        execute(new ConnectWorker(this, job));
        return job;
    }

    public synchronized Promise<MelValidationProcess, Exception, Void> connect(Long certificateId) throws SqlQueriesException, InterruptedException {
        DeferredObject<MelValidationProcess, Exception, Void> deferred = new DeferredObject<>();
        if (certificateId == null) {
            return deferred.reject(new Exception("certificateid was null"));
        }
        Certificate certificate = certificateManager.getTrustedCertificateById(certificateId);
        if (certificate == null) {
            return deferred.reject(new Exception("no certificate found for id: " + certificate));
        }
        return connectedEnvironment.connect(certificate);
    }


    Promise<MelValidationProcess, Exception, Void> connect(String address, int port, int portCert, boolean regOnUnkown) throws InterruptedException {
        return connectedEnvironment.connect(address, port, portCert, regOnUnkown);
    }

    public Set<IMelService> getMelServices() {
        uuidServiceMapSemaphore.lock();
        Set<IMelService> result = new HashSet<>(uuidServiceMap.values());
        uuidServiceMapSemaphore.unlock();
        return result;
    }

    public void setBrotCaster(MelAuthBrotCaster brotCaster) {
        this.brotCaster = brotCaster;
    }

    public MelAuthBrotCaster getBrotCaster() {
        return brotCaster;
    }

    private void addToNetworkEnvironment(Long certId, MelServicesPayload melServicesPayload) {
        networkEnvironment.add(certId, null);
        for (ServiceJoinServiceType service : melServicesPayload.getServices()) {
            networkEnvironment.add(certId, service);
        }
    }

    private void connectAndCollect(Map<String, Boolean> checkedAddresses, NetworkEnvironment networkEnvironment, Certificate intendedCertificate) throws IOException, IllegalAccessException, SqlQueriesException, URISyntaxException, ClassNotFoundException, KeyManagementException, BadPaddingException, KeyStoreException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableKeyException, CertificateException, NoSuchPaddingException, JsonSerializationException, IllegalBlockSizeException, InterruptedException {
        String address = MelAuthSocket.getAddressString(intendedCertificate.getInetAddress(), intendedCertificate.getPort().v());
        N runner = new N(e -> e.printStackTrace());
        if (!checkedAddresses.containsKey(address)) {
            checkedAddresses.put(address, true);
            Promise<MelValidationProcess, Exception, Void> authenticatedPromise = this.connect(intendedCertificate.getAddress().v(), intendedCertificate.getPort().v(), intendedCertificate.getCertDeliveryPort().v(), false);
            authenticatedPromise.done(melValidationProcess -> {
                runner.runTry(() -> {
                    Request<MelServicesPayload> servicesPromise = this.getAllowedServices(melValidationProcess.getPartnerCertificate().getId().v());
                    servicesPromise.done(melServicesPayload -> {
                        runner.runTry(() -> this.addToNetworkEnvironment(melValidationProcess.getPartnerCertificate().getId().v(), melServicesPayload));
                    });
                });
            }).fail(result -> {
                Lok.error("MelAuthService.connectAndCollect.fail() for: " + address + "");
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
            for (MelValidationProcess validationProcess : connectedEnvironment.getValidationProcesses()) {
                Long certId = validationProcess.getConnectedId();
                networkEnvironment.add(certId, null);
                checkedAddresses.put(validationProcess.getAddressString(), true);
                Request<MelServicesPayload> gotServicesPromise = this.getAllowedServices(certId);
                gotServicesPromise.done(melServicesPayload -> {
                    addToNetworkEnvironment(certId, melServicesPayload);
                });
            }
            // check DB entries
            for (Certificate intendedCertificate : certificateManager.getTrustedCertificates()) {
                connectAndCollect(checkedAddresses, networkEnvironment, intendedCertificate);
            }
            // discover, connect & collect results
            melAuthWorker.getBrotCaster().setBrotCasterListener((inetAddress, port, portCert) -> {
                runner.runTry(() -> {
                    String address = MelAuthSocket.getAddressString(inetAddress, port);
                    checkedAddresses.put(address, true);
                    Promise<MelValidationProcess, Exception, Void> promise = this.connect(inetAddress.getHostAddress(), port, portCert, false);
                    promise.done(melValidationProcess -> {
                        runner.runTry(() -> {
                            networkEnvironment.add(melValidationProcess.getPartnerCertificate().getId().v(), null);
                            getAllowedServices(melValidationProcess.getPartnerCertificate().getId().v()).done(melServicesPayload -> {
                                runner.runTry(() -> {
                                            for (ServiceJoinServiceType service : melServicesPayload.getServices()) {
                                                networkEnvironment.add(melValidationProcess.getPartnerCertificate().getId().v(), service);
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
            melAuthWorker.getBrotCaster().discover(settings.getBrotcastPort());
        });
    }

    public MelAuthService discoverNetworkEnvironment() {
        Lok.debug("creating discover job");
        melAuthWorker.addJob(new NetworkEnvDiscoveryJob());
        return this;
    }

    public NetworkEnvironment getNetworkEnvironment() {
        return networkEnvironment;
    }


    public boolean registerValidationProcess(MelValidationProcess validationProcess) {
        return connectedEnvironment.registerValidationProcess(validationProcess);
    }

    //    private static AtomicLong closeCount = new AtomicLong(0L);
    private static AtomicInteger DEBUG_count = new AtomicInteger(0);

    public void onSocketClosed(MelAuthSocket melAuthSocket) {
        connectedEnvironment.onSocketClosed(melAuthSocket);
        sockets.remove(melAuthSocket);
    }

    public void execute(MelRunnable runnable) {
        melBoot.execute(runnable);
    }

    public void setMelBoot(MelBoot melBoot) {
        this.melBoot = melBoot;
    }

    /**
     * shuts down all running services and connections.
     *
     * @return
     */
    public Promise<Void, Void, Void> shutDown() {
        ShutDownDeferredManager shutdownManager = new ShutDownDeferredManager();
        uuidServiceMapSemaphore.lock();
        for (IMelService service : uuidServiceMap.values()) {
            if (service instanceof MelService) {
                N.r(() -> {
                    MelService melService = (MelService) service;
                    shutdownManager.when(melService.shutDown());
                });
            }
        }
        uuidServiceMapSemaphore.unlock();
        Set<MelSocket> socks = new HashSet<>(sockets);
        for (MelSocket socket : socks) {
            shutdownManager.when(socket.shutDown());
        }
        for (MelAuthAdmin admin : melAuthAdmins) {
            admin.shutDown();
        }
        shutdownManager.when(melAuthWorker.shutDown());
        N.r(melBoot::shutDown);
        databaseManager.shutDown();
        return shutdownManager.digest();
    }

    public void suspend() {
        uuidServiceMapSemaphore.lock();
        for (IMelService service : uuidServiceMap.values()) {
            if (service instanceof MelService) {
                N.r(((MelService) service)::stop);
            }
        }
        uuidServiceMapSemaphore.unlock();
//        connectedEnvironment.shutDown();
        Set<MelSocket> socks = new HashSet<>(sockets);
        Lok.debug("closing no of sockets: " + socks.size());
        for (MelSocket socket : socks) {
            socket.shutDown();
        }
        melAuthWorker.shutDown();

    }

    public void resume() {
        Lok.debug("resume");
        melBoot.onHeavyWorkAllowed();
        uuidServiceMapSemaphore.lock();
        List<DeferredObject> servicesStarted = new ArrayList<>();
        for (IMelService service : uuidServiceMap.values()) {
            if (service instanceof MelService) {
                MelService melService = (MelService) service;
                DeferredObject<DeferredRunnable, Exception, Void> startedPromise = new DeferredObject<>();
                melService.setStartedPromise(startedPromise);
//                servicesStarted.add(startedPromise);
                melService.resume();
                execute(melService);
            }
        }
        uuidServiceMapSemaphore.unlock();
        // reconnect everything after resume
        melAuthWorker.addJob(new NetworkEnvDiscoveryJob());
        DeferredObject[] arr = N.arr.fromCollection(servicesStarted, N.converter(DeferredObject.class, element -> element));
        if (arr.length > 0) {
            new DefaultDeferredManager().when(arr).done(result -> execute(melAuthWorker))
                    .fail(result -> Lok.error("could not resume!"));
        } else {
            execute(melAuthWorker);
        }
    }

    public void addMelSocket(MelSocket melSocket) {
        sockets.add(melSocket);
    }

    public MelBoot getMelBoot() {
        return melBoot;
    }

    public MelServicesPayload getAllowedServicesFor(Long certId) throws SqlQueriesException {
        MelServicesPayload payload = new MelServicesPayload();
        List<ServiceJoinServiceType> services = databaseManager.getAllowedServicesJoinTypes(certId);
        //set flag for running Services, then add to result
        for (ServiceJoinServiceType service : services) {
            boolean running = getMelService(service.getUuid().v()) != null;
            service.setRunning(running);
            payload.addService(service);
        }
        return payload;
    }

    public void onMelAuthIsUp() {
        for (IMelService melService : getMelServices()) {
            melService.onServiceRegistered();
        }
    }

    public void addAllMelAuthAdmin(List<MelAuthAdmin> melAuthAdmins) {
        for (MelAuthAdmin admin : melAuthAdmins) {
            this.addMelAuthAdmin(admin);
        }
    }

    public String getCompleteNotificationText(MelNotification notification) {
        IMelService melService = getMelService(notification.getServiceUuid());
        final String serviceName = databaseManager.getServiceName(melService);
        final String serviceTypeName = databaseManager.getServiceTypeName(melService);
        return "from " + serviceTypeName + "/" + serviceName + ": " + notification.getText();
    }

    public void onNotificationFromService(IMelService melService, MelNotification notification) {
        for (MelAuthAdmin admin : melAuthAdmins) {
            notification.addProgressListener(admin);
            admin.onNotificationFromService(melService, notification);
        }
    }

    public void deleteService(String uuid) throws SqlQueriesException, InstantiationException, IllegalAccessException {
        Service service = databaseManager.getServiceByUuid(uuid);
        ServiceType serviceType = databaseManager.getServiceTypeByName(service.getName().v());
        MelService melService = getMelService(uuid);
        N.r(melService::stop);
        Bootloader bootloader = melBoot.getBootLoader(serviceType.getType().v());
        N.r(() -> bootloader.cleanUpDeletedService(melService, uuid));
        databaseManager.deleteService(service.getId().v());
    }

    public PowerManager getPowerManager() {
        return powerManager;
    }

    /**
     * services changed names or something {@link MelAuthService displays}
     */
    public void onServicesChanged() {
        for (MelAuthAdmin admin : melAuthAdmins) {
            admin.onChanged();
        }
    }

    public Updater getUpdater() {
        return updater;
    }

    public void shutDownCommunications() {
        this.melAuthWorker.shutDown();
    }

    public DeferredObject<DeferredRunnable, Exception, Void> startUpCommunications() {
        DeferredObject<DeferredRunnable, Exception, Void> deferred = new DeferredObject<>();
        if (melAuthWorker.isStopped()) {
            // restart
            melAuthWorker.setStartedPromise(deferred);
            this.execute(melAuthWorker);
        } else {
            // it is still running
            deferred.resolve(melAuthWorker);
        }
        return deferred;
    }

    public File getCacheDir() {
        return cacheDir;
    }

    private boolean isConnectedTo(Long certId) {
        AtomicBoolean connected = new AtomicBoolean(false);
        return N.result(() -> {
            Warden warden = null;
            try {
                warden = P.confine(P.read(connectedEnvironment));
                if (connectedEnvironment.getValidationProcess(certId) != null)
                    connected.set(true);
            } finally {
                if (warden != null)
                    warden.end();
            }
            return connected.get();
        }, false);
    }
}
