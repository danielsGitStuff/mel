package de.mel.auth.service;

import de.mel.Lok;
import de.mel.auth.data.MelRequest;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.socket.process.reg.IRegisterHandler;
import de.mel.auth.socket.process.reg.IRegisterHandlerListener;
import de.mel.auth.socket.process.reg.IRegisteredHandler;

import java.util.List;

public class FileSyncTestBoilerplate {
    public static IRegisterHandler registerHandlerAlwaysAccepts = new IRegisterHandler() {
        @Override
        public void acceptCertificate(IRegisterHandlerListener listener, MelRequest request, Certificate myCertificate, Certificate certificate) {
            Lok.debug("boilerplate accepts certificate");
            listener.onCertificateAccepted(request, certificate);
        }

        @Override
        public void onRegistrationCompleted(Certificate partnerCertificate) {

        }

        @Override
        public void onRemoteRejected(Certificate partnerCertificate) {

        }

        @Override
        public void onLocallyRejected(Certificate partnerCertificate) {

        }

        @Override
        public void onRemoteAccepted(Certificate partnerCertificate) {

        }

        @Override
        public void onLocallyAccepted(Certificate partnerCertificate) {

        }
    };

    public static IRegisteredHandler registeredHandlerAlwaysGrantsAllAccess = (melAuthService, registered) -> {
        Lok.debug("boilerplate grants all access");
        List<ServiceJoinServiceType> services = melAuthService.getDatabaseManager().getAllServices();
        for (ServiceJoinServiceType serviceJoinServiceType : services) {
            melAuthService.getDatabaseManager().grant(serviceJoinServiceType.getServiceId().v(), registered.getId().v());
        }
    };
}
