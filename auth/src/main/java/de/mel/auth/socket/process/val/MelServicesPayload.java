package de.mel.auth.socket.process.val;

import de.mel.auth.data.ServicePayload;
import de.mel.auth.data.db.ServiceJoinServiceType;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 5/3/16.
 */
public class MelServicesPayload extends ServicePayload {
    private List<ServiceJoinServiceType> services = new ArrayList<>();

    public MelServicesPayload addService(ServiceJoinServiceType s) {
        services.add(s);
        return this;
    }

    public List<ServiceJoinServiceType> getServices() {
        return services;
    }
}
