package de.mein.auth.service;

import de.mein.Lok;
import de.mein.auth.MeinNotification;
import de.mein.auth.data.ServicePayload;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.Job;
import de.mein.auth.socket.process.val.Request;
import org.jdeferred.Promise;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Created by xor on 12/15/16.
 */
public class MeinTestService extends MeinServiceWorker {

    public MeinTestService(MeinAuthService meinAuthService, File workingDirectory, Long serviceTypeId, String uuid) {
        super(meinAuthService, workingDirectory,serviceTypeId,uuid, Bootloader.BootLevel.SHORT);
    }

    @Override
    public void handleRequest(Request request) throws Exception {
        Lok.debug("MeinTestService.handleRequest");
    }

    @Override
    public void handleMessage(ServicePayload payload, Certificate partnerCertificate) {
        Lok.debug("MeinTestService.handleMessage");
    }

    @Override
    public void connectionAuthenticated(Certificate partnerCertificate) {
        Lok.debug("MeinTestService.connectionAuthenticated");
    }

    @Override
    public void handleCertificateSpotted(Certificate partnerCertificate) {
        Lok.debug("MeinTestService.handleCertificateSpotted");
    }

    @Override
    public void onServiceRegistered() {
        Lok.debug("MeinTestService.onServiceRegistered");
    }

    @Override
    public MeinNotification createSendingNotification() {
        return null;
    }

    @Override
    public void onCommunicationsDisabled() {

    }

    @Override
    public void onCommunicationsEnabled() {

    }

    @Override
    public void onBootLevel2Finished() {

    }

    @Override
    public void onBootLevel1Finished() {

    }


    @Override
    protected void workWork(Job job) throws Exception {
        Lok.debug("MeinTestService.workWork");
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName();
    }

    @Override
    public Promise<Void, Void, Void> onShutDown() {
        Lok.debug("MeinTestService.onShutDown");
        return null;
    }

    @Override
    protected ExecutorService createExecutorService(ThreadFactory threadFactory) {
        return Executors.newCachedThreadPool(threadFactory);
    }
}
