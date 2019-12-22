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

public class DummyMelAuthService extends MelAuthService {
    public DummyMelAuthService(MelAuthSettings melAuthSettings) {
        super(melAuthSettings);
    }

    @Override
    public MelAuthSettings getSettings() {
        return null;
    }

    @Override
    public List<Long> getConnectedUserIds() {
        return null;
    }

    @Override
    public MelAuthServiceImpl addMelAuthAdmin(MelAuthAdmin admin) {
        return null;
    }

    @Override
    public void notifyAdmins() {

    }

    @Override
    public MelAuthServiceImpl addRegisteredHandler(IRegisteredHandler IRegisteredHandler) {
        return null;
    }

    @Override
    public File getWorkingDirectory() {
        return null;
    }

    @Override
    public List<IRegisteredHandler> getRegisteredHandlers() {
        return null;
    }

    @Override
    public MelAuthServiceImpl addRegisterHandler(IRegisterHandler registerHandler) {
        return null;
    }

    @Override
    public DeferredObject<DeferredRunnable, Exception, Void> prepareStart() {
        return null;
    }

    @Override
    public void updateProgram() throws UnrecoverableKeyException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException, IOException {

    }

    @Override
    public void start() {

    }

    @Override
    public List<Certificate> getTrustedCertificates() throws SqlQueriesException {
        return null;
    }

    @Override
    public CertificateManager getCertificateManager() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public Certificate getMyCertificate() throws CertificateEncodingException {
        return null;
    }

    @Override
    public String toString() {
        return null;
    }

    @Override
    public List<IRegisterHandler> getRegisterHandlers() {
        return null;
    }

    @Override
    public DatabaseManager getDatabaseManager() {
        return null;
    }

    @Override
    public Request<MelServicesPayload> getAllowedServices(Long certificateId) throws JsonSerializationException, IllegalAccessException {
        return null;
    }

    @Override
    public MelService getMelService(String serviceUuid) {
        return null;
    }

    @Override
    public MelAuthServiceImpl registerMelService(MelService melService) throws SqlQueriesException {
        return null;
    }

    @Override
    public MelAuthServiceImpl unregisterMelService(String serviceUuid) {
        return null;
    }

    @Override
    public MelAuthServiceImpl setName(String name) {
        return null;
    }

    @Override
    public void updateCertAddresses(Long remoteCertId, String address, Integer port, Integer portCert) throws SqlQueriesException {

    }

    @Override
    public Promise<MelAuthService, Exception, Void> boot() {
        return null;
    }

    @Override
    public MelAuthServiceImpl saveApprovals(ApprovalMatrix approvalMatrix) throws SqlQueriesException {
        return null;
    }

    @Override
    <T extends MelIsolatedProcess> DeferredObject<T, Exception, Void> connectToService(Class<T> isolatedServiceClass, Long certId, String remoteServiceUuid, String ownServiceUuid, String address, Integer port, Integer portCert) throws SqlQueriesException, InterruptedException {
        return null;
    }

    @Override
    public Promise<MelValidationProcess, Exception, Void> connect(Long certificateId) throws SqlQueriesException, InterruptedException {
        return new DeferredObject<MelValidationProcess, Exception, Void>().reject(new Exception("this is a test"));
    }

    @Override
    Promise<MelValidationProcess, Exception, Void> connect(String address, int port, int portCert, boolean regOnUnkown) throws InterruptedException {
        return null;
    }

    @Override
    public Set<IMelService> getMelServices() {
        return null;
    }

    @Override
    public void setBrotCaster(MelAuthBrotCaster brotCaster) {

    }

    @Override
    public MelAuthBrotCaster getBrotCaster() {
        return null;
    }

    @Override
    protected void addToNetworkEnvironment(Long certId, MelServicesPayload melServicesPayload) {

    }

    @Override
    protected void connectAndCollect(Map<String, Boolean> checkedAddresses, NetworkEnvironment networkEnvironment, Certificate intendedCertificate) throws IOException, IllegalAccessException, SqlQueriesException, URISyntaxException, ClassNotFoundException, KeyManagementException, BadPaddingException, KeyStoreException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableKeyException, CertificateException, NoSuchPaddingException, JsonSerializationException, IllegalBlockSizeException, InterruptedException {

    }

    @Override
    void discoverNetworkEnvironmentImpl() {

    }

    @Override
    public MelAuthServiceImpl discoverNetworkEnvironment() {
        return null;
    }

    @Override
    public NetworkEnvironment getNetworkEnvironment() {
        return null;
    }

    @Override
    public boolean registerValidationProcess(MelValidationProcess validationProcess) {
        return false;
    }

    @Override
    public void onSocketClosed(MelAuthSocket melAuthSocket) {

    }

    @Override
    public void execute(MelRunnable runnable) {

    }

    @Override
    public void setMelBoot(MelBoot melBoot) {

    }

    @Override
    public Promise<Void, Void, Void> shutDown() {
        return null;
    }

    @Override
    public void suspend() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void addMelSocket(MelSocket melSocket) {

    }

    @Override
    public MelBoot getMelBoot() {
        return null;
    }

    @Override
    public MelServicesPayload getAllowedServicesFor(Long certId) throws SqlQueriesException {
        return null;
    }

    @Override
    public void onMelAuthIsUp() {

    }

    @Override
    public void addAllMelAuthAdmin(List<MelAuthAdmin> melAuthAdmins) {

    }

    @Override
    public String getCompleteNotificationText(MelNotification notification) {
        return null;
    }

    @Override
    public void onNotificationFromService(IMelService melService, MelNotification notification) {

    }

    @Override
    public void deleteService(String uuid) throws SqlQueriesException, InstantiationException, IllegalAccessException {

    }

    @Override
    public PowerManager getPowerManager() {
        return null;
    }

    @Override
    public void onServicesChanged() {

    }

    @Override
    public Updater getUpdater() {
        return null;
    }

    @Override
    public void shutDownCommunications() {

    }

    @Override
    public DeferredObject<DeferredRunnable, Exception, Void> startUpCommunications() {
        return null;
    }

    @Override
    public File getCacheDir() {
        return null;
    }

    @Override
    protected boolean isConnectedTo(Long certId) {
        return false;
    }
}
