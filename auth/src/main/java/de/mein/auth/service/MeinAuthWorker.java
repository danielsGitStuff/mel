package de.mein.auth.service;

import de.mein.DeferredRunnable;
import de.mein.auth.broadcast.MeinAuthBrotCaster;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.jobs.AConnectJob;
import de.mein.auth.jobs.Job;
import de.mein.auth.jobs.NetworkEnvDiscoveryJob;
import de.mein.auth.socket.MeinAuthSocket;
import de.mein.auth.socket.MeinAuthSocketOpener;
import de.mein.auth.socket.process.imprt.MeinAuthCertDelivery;
import de.mein.auth.socket.process.val.MeinValidationProcess;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Promise;
import org.jdeferred.impl.DefaultDeferredManager;
import org.jdeferred.impl.DeferredObject;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.CertificateException;

/**
 * Created by xor on 05.09.2016.
 */
@SuppressWarnings("Duplicates")
public class MeinAuthWorker extends MeinWorker {
    private final int port;
    private MeinAuthSocketOpener socketOpener;
    private final MeinAuthService meinAuthService;
    protected MeinAuthCertDelivery certDelivery;
    protected MeinAuthBrotCaster brotCaster;


    public MeinAuthWorker(MeinAuthService meinAuthService, MeinAuthSettings meinAuthSettings) throws Exception {
        this.certDelivery = new MeinAuthCertDelivery(meinAuthService, meinAuthSettings.getDeliveryPort());
        this.meinAuthService = meinAuthService;
        this.port = meinAuthSettings.getPort();
        brotCaster = new MeinAuthBrotCaster(meinAuthService);
        meinAuthService.setBrotCaster(brotCaster);
    }

    @Override
    public void onShutDown() {
        certDelivery.shutDown();
        brotCaster.shutDown();
        super.onShutDown();
    }

    @Override
    public void run() {
        DeferredObject<DeferredRunnable, Exception, Void> brotcasterPromise = brotCaster.getStartedDeferred();
        DeferredObject<DeferredRunnable, Exception, Void> certDeliveryPromise = certDelivery.getStartedDeferred();
        socketOpener = new MeinAuthSocketOpener(meinAuthService, port);
        DeferredObject<DeferredRunnable, Exception, Void> socketOpenerPromise = socketOpener.getStartedDeferred();
        meinAuthService.execute(brotCaster);
        meinAuthService.execute(certDelivery);
        meinAuthService.execute(socketOpener);
        new DefaultDeferredManager().when(certDeliveryPromise, socketOpenerPromise, brotcasterPromise).done(result -> {
            //say hello!
            try {
                brotCaster.brotcast(MeinAuthSettings.BROTCAST_PORT, meinAuthService.getSettings().getDiscoverMessage());
            } catch (IOException e) {
                System.err.println("brotcast went wrong :(");
                e.printStackTrace();
                startedPromise.resolve(this);
            }
            startedPromise.resolve(this);
        }).fail(result -> {
            System.out.println("MeinAuthWorker.runTry.STRANGE");
            startedPromise.reject(new Exception("keinen plan von nix"));
        });
        super.run();
    }


    @Override
    protected void workWork(Job job) throws Exception {
        System.out.println("MeinAuthWorker.workWork." + job.getClass().getSimpleName());
        if (job instanceof AConnectJob) {
            connect((AConnectJob) job);
        } else if (job instanceof NetworkEnvDiscoveryJob) {
            meinAuthService.discoverNetworkEnvironmentImpl();
        }
    }

    private void connect(AConnectJob job) throws ClassNotFoundException, IllegalAccessException, NoSuchPaddingException, URISyntaxException, SqlQueriesException, KeyManagementException, BadPaddingException, CertificateException, KeyStoreException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableKeyException, JsonSerializationException, IOException, IllegalBlockSizeException, InterruptedException {
        MeinAuthSocket meinAuthSocket = new MeinAuthSocket(meinAuthService);
        System.out.println("MeinAuthWorker.connect: " + job.getAddress() + ":" + job.getPort() + ":" + job.getPortCert() + "?reg=" + job.getRegOnUnknown());
        Promise<MeinValidationProcess, Exception, Void> promise = meinAuthSocket.connect(job);
//        promise.done(meinValidationProcess -> {
//            job.getPromise().resolve(meinValidationProcess);
//        });
        // promise.fail(ex -> job.getPromise().reject(ex));
    }

    public MeinAuthBrotCaster getBrotCaster() {
        return brotCaster;
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + meinAuthService.getName();
    }

}
