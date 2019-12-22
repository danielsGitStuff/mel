package de.mel.auth.service;

import de.mel.DeferredRunnable;
import de.mel.MelRunnable;
import de.mel.auth.MelAuthAdmin;
import de.mel.auth.MelNotification;
import de.mel.auth.broadcast.MelAuthBrotCaster;
import de.mel.auth.data.ApprovalMatrix;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.NetworkEnvironment;
import de.mel.auth.data.access.CertificateManager;
import de.mel.auth.data.access.DatabaseManager;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.socket.MelAuthSocket;
import de.mel.auth.socket.MelSocket;
import de.mel.auth.socket.MelValidationProcess;
import de.mel.auth.socket.process.reg.IRegisterHandler;
import de.mel.auth.socket.process.reg.IRegisteredHandler;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
import de.mel.auth.socket.process.val.MelServicesPayload;
import de.mel.auth.socket.process.val.Request;
import de.mel.core.serialize.exceptions.JsonSerializationException;
import de.mel.sql.SqlQueriesException;
import de.mel.update.Updater;
import org.jdeferred.Promise;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class MelAuthService {
    protected final MelAuthSettings settings;

    public MelAuthService(MelAuthSettings melAuthSettings) {
        this.settings = melAuthSettings;
    }

    public abstract MelAuthSettings getSettings();

    public abstract List<Long> getConnectedUserIds();

    public abstract MelAuthService addMelAuthAdmin(MelAuthAdmin admin);

    public abstract void notifyAdmins();

    public abstract MelAuthService addRegisteredHandler(IRegisteredHandler IRegisteredHandler);

    public abstract File getWorkingDirectory();

    public abstract List<IRegisteredHandler> getRegisteredHandlers();

    public abstract MelAuthService addRegisterHandler(IRegisterHandler registerHandler);

    public abstract DeferredObject<DeferredRunnable, Exception, Void> prepareStart();

    public abstract void updateProgram() throws UnrecoverableKeyException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException, IOException;

    public abstract void start();

    public abstract List<Certificate> getTrustedCertificates() throws SqlQueriesException;

    public abstract CertificateManager getCertificateManager();

    public abstract String getName();

    public abstract Certificate getMyCertificate() throws CertificateEncodingException;

    @Override
    public abstract String toString();

    public abstract List<IRegisterHandler> getRegisterHandlers();

    public abstract DatabaseManager getDatabaseManager();

    public abstract Request<MelServicesPayload> getAllowedServices(Long certificateId) throws JsonSerializationException, IllegalAccessException;

    public abstract MelService getMelService(String serviceUuid);

    /**
     * services must register using this method if they want to communicate over the network.
     * otherwise they won't be able to receive messages.
     *
     * @param melService
     * @return
     * @throws SqlQueriesException
     */
    public abstract MelAuthService registerMelService(MelService melService) throws SqlQueriesException;

    public abstract MelAuthService unregisterMelService(String serviceUuid);

    public abstract MelAuthService setName(String name);

    public abstract void updateCertAddresses(Long remoteCertId, String address, Integer port, Integer portCert) throws SqlQueriesException;

    public abstract Promise<MelAuthService, Exception, Void> boot();

    public abstract MelAuthService saveApprovals(ApprovalMatrix approvalMatrix) throws SqlQueriesException;

    abstract <T extends MelIsolatedProcess> DeferredObject<T, Exception, Void> connectToService(Class<T> isolatedServiceClass, Long certId, String remoteServiceUuid, String ownServiceUuid, String address, Integer port, Integer portCert) throws SqlQueriesException, InterruptedException;

    public abstract Promise<MelValidationProcess, Exception, Void> connect(Long certificateId) throws SqlQueriesException, InterruptedException;

    abstract Promise<MelValidationProcess, Exception, Void> connect(String address, int port, int portCert, boolean regOnUnkown) throws InterruptedException;

    public abstract Set<IMelService> getMelServices();

    public abstract void setBrotCaster(MelAuthBrotCaster brotCaster);

    public abstract MelAuthBrotCaster getBrotCaster();

    protected abstract void addToNetworkEnvironment(Long certId, MelServicesPayload melServicesPayload);

    protected abstract void connectAndCollect(Map<String, Boolean> checkedAddresses, NetworkEnvironment networkEnvironment, Certificate intendedCertificate) throws IOException, IllegalAccessException, SqlQueriesException, URISyntaxException, ClassNotFoundException, KeyManagementException, BadPaddingException, KeyStoreException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableKeyException, CertificateException, NoSuchPaddingException, JsonSerializationException, IllegalBlockSizeException, InterruptedException;

    /**
     * this is because Android does not like to do network stuff on GUI threads
     */
    abstract void discoverNetworkEnvironmentImpl();

    public abstract MelAuthService discoverNetworkEnvironment();

    public abstract NetworkEnvironment getNetworkEnvironment();

    public abstract boolean registerValidationProcess(MelValidationProcess validationProcess);

    public abstract void onSocketClosed(MelAuthSocket melAuthSocket);

    public abstract void execute(MelRunnable runnable);

    public abstract void setMelBoot(MelBoot melBoot);

    /**
     * shuts down all running services and connections.
     *
     * @return
     */
    public abstract Promise<Void, Void, Void> shutDown();

    public abstract void suspend();

    public abstract void resume();

    public abstract void addMelSocket(MelSocket melSocket);

    public abstract MelBoot getMelBoot();

    public abstract MelServicesPayload getAllowedServicesFor(Long certId) throws SqlQueriesException;

    public abstract void onMelAuthIsUp();

    public abstract void addAllMelAuthAdmin(List<MelAuthAdmin> melAuthAdmins);

    public abstract String getCompleteNotificationText(MelNotification notification);

    public abstract void onNotificationFromService(IMelService melService, MelNotification notification);

    public abstract void deleteService(String uuid) throws SqlQueriesException, InstantiationException, IllegalAccessException;

    public abstract PowerManager getPowerManager();

    /**
     * services changed names or something {@link MelAuthService displays}
     */
    public abstract void onServicesChanged();

    public abstract Updater getUpdater();

    public abstract void shutDownCommunications();

    public abstract DeferredObject<DeferredRunnable, Exception, Void> startUpCommunications();

    public abstract File getCacheDir();

    protected abstract boolean isConnectedTo(Long certId);
}
