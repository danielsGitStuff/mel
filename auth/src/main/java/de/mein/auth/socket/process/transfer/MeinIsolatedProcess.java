package de.mein.auth.socket.process.transfer;

import de.mein.Lok;
import de.mein.auth.MeinStrings;
import de.mein.auth.jobs.BlockReceivedJob;
import de.mein.auth.service.IMeinService;
import de.mein.auth.socket.MeinAuthSocket;
import de.mein.auth.socket.MeinProcess;
import de.mein.auth.tools.ByteTools;
import de.mein.auth.tools.N;
import de.mein.core.serialize.SerializableEntity;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Process is exclusively bound a {@link IMeinService}. It does not interfere with other connections. Good for transferring large amounts of data.
 * {@link de.mein.auth.service.MeinAuthService} does not take care of this connection. You got to do this in your Service.
 */
public abstract class MeinIsolatedProcess extends MeinProcess {

    private final String isolatedUuid;
    protected IMeinService service;
    protected Set<IsolatedProcessListener> isolatedProcessListeners = new HashSet<>();

    public void addIsolatedProcessListener(IsolatedProcessListener listener) {
        this.isolatedProcessListeners.add(listener);
    }


    public void setService(IMeinService service) {
        this.service = service;
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

    public MeinIsolatedProcess(MeinAuthSocket meinAuthSocket, IMeinService meinService, Long partnerCertificateId, String partnerServiceUuid, String isolatedUuid) {
        super(meinAuthSocket);
        meinAuthSocket.allowIsolation();
        this.partnerCertificateId = partnerCertificateId;
        this.partnerServiceUuid = partnerServiceUuid;
        this.service = meinService;
        this.isolatedUuid = isolatedUuid;
    }

    public static MeinIsolatedProcess instance(Class<? extends MeinIsolatedProcess> clazz, MeinAuthSocket meinAuthSocket, IMeinService meinService, Long partnerCertificateId, String partnerServiceUuid, String isolatedUuid) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        return clazz.getDeclaredConstructor(MeinAuthSocket.class, IMeinService.class, Long.class, String.class, String.class).newInstance(meinAuthSocket, meinService, partnerCertificateId, partnerServiceUuid, isolatedUuid);
    }


    public void onIsolated() {
        isolatedPromise.resolve(null);
    }

    @Override
    public void onMessageReceived(SerializableEntity deserialized, MeinAuthSocket webSocket) {
        Lok.debug("MeinIsolatedProcess.onMessageReceived");
    }

    public Promise<Void, Exception, Void> sendIsolate() {
        Lok.debug("MeinIsolatedProcess.sendIsolate");
        meinAuthSocket.send(MeinStrings.msg.MODE_ISOLATE);
        meinAuthSocket.setIsolated(true);
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
        return meinAuthSocket.isOpen();
    }

    public static interface IsolatedProcessListener {
        void onIsolatedSocketClosed();
    }

    @Override
    public void onSocketClosed(int code, String reason, boolean remote) {
        super.onSocketClosed(code, reason, remote);
        N.forEachIgnorantly(isolatedProcessListeners, IsolatedProcessListener::onIsolatedSocketClosed);
    }
}
