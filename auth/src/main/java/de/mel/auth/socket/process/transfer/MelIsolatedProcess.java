package de.mel.auth.socket.process.transfer;

import de.mel.Lok;
import de.mel.auth.MelStrings;
import de.mel.auth.jobs.BlockReceivedJob;
import de.mel.auth.service.IMelService;
import de.mel.auth.service.MelAuthServiceImpl;
import de.mel.auth.socket.MelAuthSocket;
import de.mel.auth.socket.MelProcess;
import de.mel.auth.tools.ByteTools;
import de.mel.auth.tools.N;
import de.mel.core.serialize.SerializableEntity;


import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Process is exclusively bound a {@link IMelService}. It does not interfere with other connections. Good for transferring large amounts of data.
 * {@link MelAuthServiceImpl} does not take care of this connection. You got to do this in your Service.
 */
public abstract class MelIsolatedProcess extends MelProcess {

    private final String isolatedUuid;
    protected IMelService service;
    protected Set<IsolatedProcessListener> isolatedProcessListeners = new HashSet<>();

    public void addIsolatedProcessListener(IsolatedProcessListener listener) {
        this.isolatedProcessListeners.add(listener);
    }


    @Deprecated // todo replace with constructor argument
    public void setService(IMelService service) {
        this.service = service;
    }

    public IMelService getService() {
        return service;
    }

    private final Long partnerCertificateId;
    private final String partnerServiceUuid;
    private DeferredObject isolatedPromise = new DeferredObject();


    public Long getPartnerCertificateId() {
        return partnerCertificateId;
    }

    public String getIsolatedUuid() {
        return isolatedUuid;
    }

    public String getPartnerServiceUuid() {
        return partnerServiceUuid;
    }

    public MelIsolatedProcess(MelAuthSocket melAuthSocket, IMelService melService, Long partnerCertificateId, String partnerServiceUuid, String isolatedUuid) {
        super(melAuthSocket);
        melAuthSocket.allowIsolation();
        this.partnerCertificateId = partnerCertificateId;
        this.partnerServiceUuid = partnerServiceUuid;
        this.service = melService;
        this.isolatedUuid = isolatedUuid;
    }

    public static MelIsolatedProcess instance(Class<? extends MelIsolatedProcess> clazz, MelAuthSocket melAuthSocket, IMelService melService, Long partnerCertificateId, String partnerServiceUuid, String isolatedUuid) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        return clazz.getDeclaredConstructor(MelAuthSocket.class, IMelService.class, Long.class, String.class, String.class).newInstance(melAuthSocket, melService, partnerCertificateId, partnerServiceUuid, isolatedUuid);
    }


    public void onIsolated() {
        isolatedPromise.resolve(null);
    }

    @Override
    public void onMessageReceived(SerializableEntity deserialized, MelAuthSocket webSocket) {
        Lok.debug("MelIsolatedProcess.onMessageReceived");
    }

    public Promise<Void, Exception, Void> sendIsolate() {
        Lok.debug("MelIsolatedProcess.sendIsolate");
        melAuthSocket.send(MelStrings.msg.MODE_ISOLATE);
        melAuthSocket.setIsolated(true);
        return isolatedPromise;
    }


    public abstract void onBlockReceived(BlockReceivedJob bytes);


    private long readTransferOffSet(byte[] block) {
        return ByteTools.bytesToLong(Arrays.copyOfRange(block, 6, 14));
    }

    private long readTransferLength(byte[] block) {
        return ByteTools.bytesToLong(Arrays.copyOfRange(block, 15, 23));
    }

    public boolean isOpen() {
        return melAuthSocket.isOpen();
    }

    public static interface IsolatedProcessListener {
        void onIsolatedProcessEnds(MelIsolatedProcess isolatedProcess);
    }

    @Override
    public void onSocketClosed(int code, String reason, boolean remote) {
        super.onSocketClosed(code, reason, remote);
        // remove thyself from service
        service.onIsolatedConnectionClosed(this);
        // tell everyone else who is interested that we're closed
        Set<IsolatedProcessListener> listeners = new HashSet<>(isolatedProcessListeners);
        N.forEachIgnorantly(listeners, isolatedProcessListener -> isolatedProcessListener.onIsolatedProcessEnds(this));
    }
}
