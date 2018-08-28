package de.mein.auth.socket.process.val;

import de.mein.auth.data.IPayload;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceJoinServiceType;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 5/3/16.
 */
public class MeinServicesPayload implements IPayload {
    private List<ServiceJoinServiceType> services = new ArrayList<>();

    public MeinServicesPayload addService(ServiceJoinServiceType s) {
        services.add(s);
        return this;
    }

    public List<ServiceJoinServiceType> getServices() {
        return services;
    }
}
