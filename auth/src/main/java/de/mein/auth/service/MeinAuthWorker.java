package de.mein.auth.service;

import de.mein.DeferredRunnable;
import de.mein.Lok;
import de.mein.auth.broadcast.MeinAuthBrotCaster;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.jobs.AConnectJob;
import de.mein.auth.jobs.Job;
import de.mein.auth.jobs.NetworkEnvDiscoveryJob;
import de.mein.auth.service.power.PowerManager;
import de.mein.auth.socket.MeinAuthSocketOpener;
import de.mein.auth.socket.process.imprt.MeinAuthCertDelivery;
import de.mein.auth.tools.N;

import org.jdeferred.impl.DefaultDeferredManager;
import org.jdeferred.impl.DeferredObject;

/**
 * Created by xor on 05.09.2016.
 */
@SuppressWarnings("Duplicates")
public class MeinAuthWorker extends MeinWorker implements PowerManager.IPowerStateListener<PowerManager> {
    private final int port;
    private final Integer deliverPort;
    private MeinAuthSocketOpener socketOpener;
    private final MeinAuthService meinAuthService;
    protected MeinAuthCertDelivery certDelivery;
    protected MeinAuthBrotCaster brotCaster;


    public MeinAuthWorker(MeinAuthService meinAuthService, MeinAuthSettings meinAuthSettings) {
        this.meinAuthService = meinAuthService;
        this.port = meinAuthSettings.getPort();
        this.deliverPort = meinAuthSettings.getDeliveryPort();
        meinAuthService.getPowerManager().addStateListener(this);
    }

    @Override
    public void run() {
        // initialize everything and then wait for things to happen
        brotCaster = new MeinAuthBrotCaster(meinAuthService);
        meinAuthService.setBrotCaster(brotCaster);
        certDelivery = new MeinAuthCertDelivery(meinAuthService, deliverPort);
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
                Lok.debug("brotcasting Hello");
                meinAuthService.discoverNetworkEnvironment();
//                brotCaster.brotcast(MeinAuthSettings.BROTCAST_PORT, meinAuthService.getSettings().getDiscoverMessage());
                meinAuthService.onMeinAuthIsUp();
            } catch (Exception e) {
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
        if (job instanceof NetworkEnvDiscoveryJob) {
            meinAuthService.discoverNetworkEnvironmentImpl();
        }
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


    @Override
    public void onStateChanged(PowerManager powerManager) {
        if (powerManager.heavyWorkAllowed() && powerManager.isWifi())
            N.thread(meinAuthService::discoverNetworkEnvironment);
    }
}
