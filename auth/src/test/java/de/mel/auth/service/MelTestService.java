package de.mel.auth.service;

import de.mel.Lok;
import de.mel.auth.MelNotification;
import de.mel.auth.data.ServicePayload;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.jobs.Job;
import de.mel.auth.socket.process.val.Request;
import org.jdeferred.Promise;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Created by xor on 12/15/16.
 */
public class MelTestService extends MelServiceWorker {

    public MelTestService(MelAuthServiceImpl melAuthService, File workingDirectory, Long serviceTypeId, String uuid) {
        super(melAuthService, workingDirectory,serviceTypeId,uuid, Bootloader.BootLevel.SHORT);
    }

    @Override
    public void handleRequest(Request request) throws Exception {
        Lok.debug("MelTestService.handleRequest");
    }

    @Override
    public void handleMessage(ServicePayload payload, Certificate partnerCertificate) {
        Lok.debug("MelTestService.handleMessage");
    }

    @Override
    public void connectionAuthenticated(Certificate partnerCertificate) {
        Lok.debug("MelTestService.connectionAuthenticated");
    }

    @Override
    public void handleCertificateSpotted(Certificate partnerCertificate) {
        Lok.debug("MelTestService.handleCertificateSpotted");
    }

    @Override
    public void onServiceRegistered() {
        Lok.debug("MelTestService.onServiceRegistered");
    }

    @Override
    public MelNotification createSendingNotification() {
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
        Lok.debug("MelTestService.workWork");
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName();
    }

    @Override
    public Promise<Void, Void, Void> onShutDown() {
        Lok.debug("MelTestService.onShutDown");
        return null;
    }

    @Override
    protected ExecutorService createExecutorService(ThreadFactory threadFactory) {
        return Executors.newCachedThreadPool(threadFactory);
    }
}
