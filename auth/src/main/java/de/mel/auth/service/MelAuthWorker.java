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

import de.mel.auth.tools.Promises;
import fun.with.promise.Promise;
import fun.with.promise.PromiseImpl;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
        PromiseImpl<DeferredRunnable, Exception> brotcasterPromise = brotCaster.getStartedDeferred();
        PromiseImpl<DeferredRunnable, Exception> certDeliveryPromise = certDelivery.getStartedDeferred();
        socketOpener = new MelAuthSocketOpener(melAuthService, port);
        PromiseImpl<DeferredRunnable, Exception> socketOpenerPromise = socketOpener.getStartedDeferred();
        // fusedAnd drops the reason, so the first part that fails keeps it here
        AtomicReference<Exception> firstFailure = new AtomicReference<>();
        for (PromiseImpl<DeferredRunnable, Exception> part : List.of(socketOpenerPromise, certDeliveryPromise, brotcasterPromise))
            part.onError(e -> firstFailure.compareAndSet(null, e));
        melAuthService.execute(brotCaster);
        melAuthService.execute(certDelivery);
        melAuthService.execute(socketOpener);
        Promise.fusedAnd(certDeliveryPromise, socketOpenerPromise, brotcasterPromise).onDone(result -> {
            //say hello!
            try {
                Lok.debug("brotcasting Hello");
                melAuthService.discoverNetworkEnvironment();
//                brotCaster.brotcast(MelAuthSettings.BROTCAST_PORT, melAuthService.getSettings().getDiscoverMessage());
                melAuthService.onMelAuthIsUp();
            } catch (Exception e) {
                Lok.error("brotcast went wrong :(");
                e.printStackTrace();
            }
            // saying hello is no condition for being up
            resolveStarted();
        }).onError(result -> {
            Exception reason = firstFailure.get();
            Lok.error("MelAuthWorker: could not start: " + (reason == null ? "unknown reason" : reason.getMessage()));
            rejectStarted(new MelAuthStartException(reason));
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
    public Promise<Void, Void> onShutDown() {
        // the parts are null if run() never came (a boot that failed before the start)
        return Promises.allSettled(certDelivery == null ? null : certDelivery.shutDown(),
                brotCaster == null ? null : brotCaster.shutDown(),
                socketOpener == null ? null : socketOpener.shutDown(),
                super.onShutDown());
    }


    @Override
    public void onStateChanged(PowerManager powerManager) {
        if (powerManager.heavyWorkAllowed() && powerManager.isWifi())
            N.thread(melAuthService::discoverNetworkEnvironment);
    }
}
