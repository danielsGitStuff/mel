package de.mein.auth.service;

import de.mein.auth.data.IPayload;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.jobs.Job;
import de.mein.auth.socket.process.val.Request;

/**
 * Created by xor on 12/15/16.
 */
public class MeinTestService extends MeinServiceWorker {

    public MeinTestService(MeinAuthService meinAuthService) {
        super(meinAuthService);
    }

    @Override
    public void handleRequest(Request request) throws Exception {
        System.out.println("MeinTestService.handleRequest");
    }

    @Override
    public void handleMessage(IPayload payload, Certificate partnerCertificate, String intent) {
        System.out.println("MeinTestService.handleMessage");
    }

    @Override
    public void connectionAuthenticated(Certificate partnerCertificate) {
        System.out.println("MeinTestService.connectionAuthenticated");
    }

    @Override
    public void handleCertificateSpotted(Certificate partnerCertificate) {
        System.out.println("MeinTestService.handleCertificateSpotted");
    }



    @Override
    protected void workWork(Job job) throws Exception {
        System.out.println("MeinTestService.workWork");
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName();
    }

}
