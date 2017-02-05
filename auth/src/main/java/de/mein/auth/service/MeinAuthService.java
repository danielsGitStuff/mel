package de.mein.auth.service;


import de.mein.MeinRunnable;
import de.mein.auth.MeinAuthAdmin;
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
import de.mein.auth.socket.MeinAuthSocket;
import de.mein.auth.socket.ShamefulSelfConnectException;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisteredHandler;
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess;
import de.mein.auth.socket.process.val.MeinServicesPayload;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.NoTryRunner;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.sql.SqlQueriesException;
import de.mein.sql.con.SQLConnection;
import de.mein.sql.deserialize.factories.PairDeserializerFactory;
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
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by xor on 2/14/16.
 */
public class MeinAuthService extends MeinRunnable {
    static {
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
    }

    private static Logger logger = Logger.getLogger(MeinAuthService.class.getName());
    public static final String SERVICE_NAME = "meinauth";
    public static final String INTENT_REGISTER = "reg";
    public static final String INTENT_AUTH = "auth";
    public static final String INTENT_GET_SERVICES = "getservices";
    private final MeinAuthSettings settings;
    private MeinAuthWorker meinAuthWorker;
    protected final CertificateManager certificateManager;
    private final IDBCreatedListener dbCreatedListener;
    private List<MeinAuthAdmin> meinAuthAdmins = new ArrayList<>();
    private final DatabaseManager databaseManager;
    private final File workingDirectory;
    protected String name;
    protected List<IRegisterHandler> registerHandlers = new ArrayList<>();
    private List<IRegisteredHandler> registeredHandlers = new ArrayList<>();
    private NetworkEnvironment networkEnvironment = new NetworkEnvironment();

    private ConnectedEnvironment connectedEnvironment = new ConnectedEnvironment();

    private Map<String, IMeinService> uuidServiceMap = new ConcurrentHashMap<>();
    private MeinAuthBrotCaster brotCaster;


    public MeinAuthService(MeinAuthSettings meinAuthSettings, IDBCreatedListener dbCreatedListener) throws Exception {
        this.workingDirectory = meinAuthSettings.getWorkingDirectory();
        this.databaseManager = new DatabaseManager(meinAuthSettings);
        this.certificateManager = new CertificateManager(workingDirectory, databaseManager.getSqlQueries(), 1024);
        this.name = meinAuthSettings.getName();
        this.settings = meinAuthSettings;
        this.dbCreatedListener = dbCreatedListener;
        if (this.databaseManager.hadToInitialize() && this.dbCreatedListener != null)
            this.dbCreatedListener.onDBcreated(this.databaseManager);
        addRegisteredHandler((meinAuthService, registered) -> notifyAdmins());
        this.meinAuthWorker = new MeinAuthWorker(this, meinAuthSettings);

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
        return this;
    }

    public void notifyAdmins() {
        for (MeinAuthAdmin admin : meinAuthAdmins) {
            admin.onChanged();
        }
    }

    public MeinAuthService(MeinAuthSettings meinAuthSettings) throws Exception {
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

    @Override
    public void run() {
        logger.log(Level.FINER,"MeinAuthService.runTry.listening...");
        while (!Thread.currentThread().isInterrupted()) {
            try {
               /* Socket socket = this.serverSocket.accept();
                MeinSocket meinSocket = new MeinAuthSocket(this, socket);
                meinSocket.start();*/
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        logger.log(Level.FINER,"MeinAuthService.runTry.end");
    }

    @Override
    public DeferredObject<MeinRunnable, Exception, Void> start() {
        DeferredObject<MeinRunnable, Exception, Void> promise = meinAuthWorker.start();
        for (MeinAuthAdmin admin : meinAuthAdmins) {
            admin.start(this);
        }
        //super.start();
        return promise;
    }

    public List<Certificate> getImportedCerts() throws SqlQueriesException {
        List<Certificate> certs = certificateManager.getCertificates();
        return certs;
    }


    public CertificateManager getCertificateManager() {
        return certificateManager;
    }

    public String getName() {
        return name;
    }

    public Certificate getMyCertificate() throws CertificateEncodingException {
        Certificate certificate = new Certificate();
        certificate.setCertificate(this.certificateManager.getMyX509Certificate().getEncoded());
        certificate.setName(name);
        return certificate;
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + "." + name;
    }


    public List<IRegisterHandler> getRegisterHandlers() {
        return registerHandlers;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }


    public Request<MeinServicesPayload> getAllowedServices(Long certificateId) throws JsonSerializationException, IllegalAccessException {
        MeinValidationProcess validationProcess = connectedEnvironment.getValidationProcess(certificateId);
        Request<MeinServicesPayload> promise = validationProcess.request(MeinAuthService.SERVICE_NAME, MeinAuthService.INTENT_GET_SERVICES, null);
        return promise;
    }


    public IMeinService getMeinService(String serviceUuid) {
        return uuidServiceMap.get(serviceUuid);
    }


    public MeinAuthService registerMeinService(IMeinService meinService) throws SqlQueriesException {
        uuidServiceMap.put(meinService.getUuid(), meinService);
        notifyAdmins();
        return this;
    }

    public MeinAuthService unregisterMeinService(Long serviceUuid) {
        uuidServiceMap.remove(serviceUuid);
        notifyAdmins();
        return this;
    }


    public MeinAuthService setName(String name) {
        this.name = name;
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
        Certificate c = certificateManager.getCertificateById(remoteCertId);
        c.setAddress(address).setCertDeliveryPort(portCert).setPort(port);
        certificateManager.updateCertificate(c);
    }


    public Promise<MeinAuthService, Exception, Void> boot() {
        DeferredObject<MeinAuthService, Exception, Void> bootedPromise = new DeferredObject<>();
        this.start();
        logger.log(Level.FINE,"MeinAuthService.boot...");
       /* while (!this.runs()) {
            // we gotta println this or java won't notice 'this.runs()' actually returns true
            System.out.print(this.runs());
        }*/
        bootedPromise.resolve(this);
        return bootedPromise;
    }

    public MeinAuthService saveApprovals(ApprovalMatrix approvalMatrix) throws SqlQueriesException {
        databaseManager.saveApprovals(approvalMatrix);
        return this;
    }

    public <T extends MeinIsolatedProcess> DeferredObject<T, Exception, Void> connectToService(Class<T> isolatedServiceClass, Long certId, String remoteServiceUuid, String ownServiceUuid, String address, Integer port, Integer portCert) throws SqlQueriesException, InterruptedException {
        Certificate certificate = certificateManager.getCertificateById(certId);
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

    public Promise<MeinValidationProcess, Exception, Void> connect(Long certificateId) throws SqlQueriesException, InterruptedException {
        DeferredObject<MeinValidationProcess, Exception, Void> deferred = new DeferredObject<>();
        MeinValidationProcess mvp;
        Certificate certificate = certificateManager.getCertificateById(certificateId);
        // check if already connected via id and address
        if (certificateId != null && (mvp = connectedEnvironment.getValidationProcess(certificateId)) != null) {
            deferred.resolve(mvp);
        } else if ((mvp = connectedEnvironment.getValidationProcess(certificate.getAddress().v())) != null) {
            deferred.resolve(mvp);
        } else {
            ConnectJob job = new ConnectJob(certificateId, certificate.getAddress().v(), certificate.getPort().v(), certificate.getCertDeliveryPort().v(), false);
            job.getPromise().done(result -> deferred.resolve((MeinValidationProcess) result)).fail(result -> deferred.reject(result));
            meinAuthWorker.addJob(job);
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
        return new HashSet<>(uuidServiceMap.values());
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
        NoTryRunner runner = new NoTryRunner(e -> e.printStackTrace());
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
                logger.log(Level.SEVERE,"MeinAuthService.connectAndCollect.fail");
            });
        }
    }

    public MeinAuthService discoverNetworkEnvironment() {
        NoTryRunner runner = new NoTryRunner(e -> e.printStackTrace());
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
                    logger.log(Level.SEVERE,"MeinAuthService.discoverNetworkEnvironment.NOT.IMPLEMENTED.YET");
                    addToNetworkEnvironment(certId, meinServicesPayload);
                });
            }
            // check DB entries
            for (Certificate intendedCertificate : certificateManager.getCertificates()) {
                connectAndCollect(checkedAddresses, networkEnvironment, intendedCertificate);
            }
            // discover, connect & collect results
            meinAuthWorker.getBrotCaster().setBrotCasterListener((inetAddress, port, portCert) -> {
                runner.runTry(() -> {
                    String address = MeinAuthSocket.getAddressString(inetAddress, port);
                   // if (!checkedAddresses.containsKey(address)) {
                        checkedAddresses.put(address, true);
                        Object ashhh = InetAddress.getByName("localhost").getHostAddress();
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
                  //  }
                });
            });
            meinAuthWorker.getBrotCaster().discover(settings.getBrotcastPort());
        });
        return this;
    }

    public NetworkEnvironment getNetworkEnvironment() {
        return networkEnvironment;
    }


    public void onSocketAuthenticated(MeinValidationProcess validationProcess) {
        this.connectedEnvironment.addValidationProcess(validationProcess);
    }


}
