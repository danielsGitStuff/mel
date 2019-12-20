package de.mel.auth.service;

import de.mel.DeferredRunnable;
import de.mel.Lok;
import de.mel.auth.broadcast.MelAuthBrotCaster;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.jobs.Job;
import de.mel.auth.jobs.NetworkEnvDiscoveryJob;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.socket.MelAuthSocketOpener;
import de.mel.auth.socket.process.imprt.MelAuthCertDelivery;
import de.mel.auth.tools.N;

import de.mel.auth.tools.ShutDownDeferredManager;
import org.jdeferred.impl.DefaultDeferredManager;
import org.jdeferred.impl.DeferredObject;

/**
 * Created by xor on 05.09.2016.
 */
@SuppressWarnings("Duplicates")
public class MelAuthWorker extends MelWorker implements PowerManager.IPowerStateListener<PowerManager> {
    private final int port;
    private final Integer deliverPort;
    private MelAuthSocketOpener socketOpener;
    private final MelAuthServiceImpl melAuthService;
    protected MelAuthCertDelivery certDelivery;
    protected MelAuthBrotCaster brotCaster;


    public MelAuthWorker(MelAuthServiceImpl melAuthService, MelAuthSettings melAuthSettings) {
        this.melAuthService = melAuthService;
        this.port = melAuthSettings.getPort();
        this.deliverPort = melAuthSettings.getDeliveryPort();
        melAuthService.getPowerManager().addStateListener(this);
    }

    @Override
    public void run() {
        // initialize everything and then wait for things to happen
        brotCaster = new MelAuthBrotCaster(melAuthService);
        melAuthService.setBrotCaster(brotCaster);
        certDelivery = new MelAuthCertDelivery(melAuthService, deliverPort);
        DeferredObject<DeferredRunnable, Exception, Void> brotcasterPromise = brotCaster.getStartedDeferred();
        DeferredObject<DeferredRunnable, Exception, Void> certDeliveryPromise = certDelivery.getStartedDeferred();
        socketOpener = new MelAuthSocketOpener(melAuthService, port);
        DeferredObject<DeferredRunnable, Exception, Void> socketOpenerPromise = socketOpener.getStartedDeferred();
        melAuthService.execute(brotCaster);
        melAuthService.execute(certDelivery);
        melAuthService.execute(socketOpener);
        new DefaultDeferredManager().when(certDeliveryPromise, socketOpenerPromise, brotcasterPromise).done(result -> {
            //say hello!
            try {
                Lok.debug("brotcasting Hello");
                melAuthService.discoverNetworkEnvironment();
//                brotCaster.brotcast(MelAuthSettings.BROTCAST_PORT, melAuthService.getSettings().getDiscoverMessage());
                melAuthService.onMelAuthIsUp();
            } catch (Exception e) {
                Lok.error("brotcast went wrong :(");
                e.printStackTrace();
                startedPromise.resolve(this);
            }
            startedPromise.resolve(this);
        }).fail(result -> {
            Lok.debug("MelAuthWorker.runTry.STRANGE");
            startedPromise.reject(new Exception("keinen plan von nix"));
        });
        // wait for work
        super.run();
    }

    @Override
    protected void workWork(Job job) throws Exception {
        Lok.debug("MelAuthWorker.workWork." + job.getClass().getSimpleName());
        if (job instanceof NetworkEnvDiscoveryJob) {
            melAuthService.discoverNetworkEnvironmentImpl();
        }
    }


    public MelAuthBrotCaster getBrotCaster() {
        return brotCaster;
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName() + " for " + melAuthService.getName();
    }


    @Override
    public DeferredObject<Void, Void, Void> onShutDown() {
        return new ShutDownDeferredManager()
                .when(certDelivery.shutDown(), brotCaster.shutDown(), socketOpener.shutDown()
                        , super.onShutDown())
                .digest();
    }


    @Override
    public void onStateChanged(PowerManager powerManager) {
        if (powerManager.heavyWorkAllowed() && powerManager.isWifi())
            N.thread(melAuthService::discoverNetworkEnvironment);
    }
}
