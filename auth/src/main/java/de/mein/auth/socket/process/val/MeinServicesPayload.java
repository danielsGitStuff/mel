package de.mein.auth.socket.process.val;

import de.mein.auth.data.ServicePayload;
import de.mein.auth.data.db.ServiceJoinServiceType;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 5/3/16.
 */
public class MeinServicesPayload extends ServicePayload {
    private List<ServiceJoinServiceType> services = new ArrayList<>();

    public MeinServicesPayload addService(ServiceJoinServiceType s) {
        services.add(s);
        return this;
    }

    public List<ServiceJoinServiceType> getServices() {
        return services;
    }
}
