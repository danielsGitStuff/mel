package de.mein.auth.socket.process.transfer;

import de.mein.auth.MeinStrings;
import de.mein.auth.service.IMeinService;
import de.mein.auth.socket.MeinAuthSocket;
import de.mein.auth.socket.MeinProcess;
import de.mein.auth.tools.ByteTools;
import de.mein.core.serialize.SerializableEntity;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/**
 * Created by xor on 12/12/16.
 */
public abstract class MeinIsolatedProcess extends MeinProcess{

    private final String isolatedUuid;
    protected IMeinService service;


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
        this.isolatedUuid=isolatedUuid;
    }

    public static MeinIsolatedProcess instance(Class<? extends MeinIsolatedProcess> clazz, MeinAuthSocket meinAuthSocket, IMeinService meinService, Long partnerCertificateId, String partnerServiceUuid, String isolatedUuid) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        return clazz.getConstructor(MeinAuthSocket.class, IMeinService.class, Long.class, String.class,String.class).newInstance(meinAuthSocket, meinService, partnerCertificateId, partnerServiceUuid,isolatedUuid);
    }


    public void onIsolated() {
        isolatedPromise.resolve(null);
    }

    @Override
    public void onMessageReceived(SerializableEntity deserialized, MeinAuthSocket webSocket) {
        System.out.println("MeinIsolatedProcess.onMessageReceived");
    }

    public Promise<Void, Exception, Void> sendIsolate() {
        System.out.println("MeinIsolatedProcess.sendIsolate");
        meinAuthSocket.send(MeinStrings.msg.MODE_ISOLATE);
        meinAuthSocket.setIsolated(true);
        return isolatedPromise;
    }



    public abstract void onBlockReceived(byte[] bytes);


    private long readTransferOffSet(byte[] block) {
        return ByteTools.bytesToLong(Arrays.copyOfRange(block, 6, 14));
    }

    private long readTransferLength(byte[] block) {
        return ByteTools.bytesToLong(Arrays.copyOfRange(block, 15, 23));
    }

    public boolean isOpen() {
        return meinAuthSocket.isOpen();
    }
}
