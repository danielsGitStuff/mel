package de.mein.drive.service;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.service.IDBCreatedListener;
import de.mein.auth.service.MeinAuthService;

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
        addRegisterHandler((listener, request, myCertificate, certificate) -> listener.onCertificateAccepted(request, certificate));
        registerMeinService(new MeinDriveServerService(this));
    }

    public MeinDriveStandAlone(MeinAuthSettings meinAuthSettings) throws Exception {
        super(meinAuthSettings);
    }
}
