package de.mein.drive.service;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.MeinRequest;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.service.IDBCreatedListener;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.socket.process.reg.IRegisterHandler;
import de.mein.auth.socket.process.reg.IRegisterHandlerListener;

/**
 * Created by xor on 09.07.2016.
 */
public class MeinDriveStandAlone extends MeinAuthService {

    public MeinDriveStandAlone(MeinAuthSettings meinAuthSettings, IDBCreatedListener dbCreatedListener) throws Exception {
        super(meinAuthSettings, databaseManager -> {
            ServiceType serviceType;
            if ((serviceType = databaseManager.getServiceTypeByName(SERVICE_NAME)) == null) {
                serviceType = databaseManager.createServiceType(SERVICE_NAME, "it is diagram stuff");
            }
            databaseManager.createService(serviceType.getId().v(), "example name");
        });
        //Dev stuff
        addRegisterHandler(new IRegisterHandler() {
            @Override
            public void acceptCertificate(IRegisterHandlerListener listener, MeinRequest request, Certificate myCertificate, Certificate certificate) {
                listener.onCertificateAccepted(request, certificate);
            }

            @Override
            public void onRegistrationCompleted(Certificate partnerCertificate) {

            }
        });
        registerMeinService(new MeinDriveServerService(this));
    }

    public MeinDriveStandAlone(MeinAuthSettings meinAuthSettings) throws Exception {
        super(meinAuthSettings);
    }
}
