package de.mein.auth.service;

import de.mein.DeferredRunnable;
import de.mein.Lok;
import de.mein.auth.broadcast.MeinAuthBrotCaster;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.jobs.AConnectJob;
import de.mein.auth.jobs.IsolatedConnectJob;
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
    public void run() {
        // initialize everything and then wait for things to happen
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
                meinAuthService.onMeinAuthIsUp();
            } catch (IOException e) {
                Lok.error("brotcast went wrong :(");
                e.printStackTrace();
                startedPromise.resolve(this);
            }
            startedPromise.resolve(this);
        }).fail(result -> {
            Lok.debug("MeinAuthWorker.runTry.STRANGE");
            startedPromise.reject(new Exception("keinen plan von nix"));
        });
        // wait for work
        super.run();
    }


    @Override
    protected void workWork(Job job) throws Exception {
        Lok.debug("MeinAuthWorker.workWork." + job.getClass().getSimpleName());
        if (job instanceof AConnectJob) {
            connect((AConnectJob) job);
        } else if (job instanceof NetworkEnvDiscoveryJob) {
            meinAuthService.discoverNetworkEnvironmentImpl();
        }
    }

    @Override
    public void addJob(Job job) {
        if(job instanceof IsolatedConnectJob){
            Lok.debug("MeinAuthWorker.addJob.debugf0e4");
        }
        super.addJob(job);
    }

    private void connect(AConnectJob job) throws ClassNotFoundException, IllegalAccessException, NoSuchPaddingException, URISyntaxException, SqlQueriesException, KeyManagementException, BadPaddingException, CertificateException, KeyStoreException, NoSuchAlgorithmException, InvalidKeyException, UnrecoverableKeyException, JsonSerializationException, IOException, IllegalBlockSizeException, InterruptedException {
        MeinAuthSocket meinAuthSocket = new MeinAuthSocket(meinAuthService);
        Lok.debug("MeinAuthWorker.connect: " + job.getAddress() + ":" + job.getPort() + ":" + job.getPortCert() + "?reg=" + job.getRegOnUnknown());
        Promise<MeinValidationProcess, Exception, Void> promise = meinAuthSocket.connect(job);
//        promise.done(meinValidationProcess -> {
//            job.getPromise().resolve(meinValidationProcess);
//        });
//        promise.fail(ex -> job.getPromise().reject(ex));
    }

    public MeinAuthBrotCaster getBrotCaster() {
        return brotCaster;
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + meinAuthService.getName();
    }


    @Override
    public void onShutDown() {
        certDelivery.shutDown();
        brotCaster.shutDown();
        socketOpener.shutDown();
        super.onShutDown();
    }
}
