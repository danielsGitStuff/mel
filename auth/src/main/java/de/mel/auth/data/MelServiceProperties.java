package de.mel.auth.data;

import de.mel.auth.service.IMelService;

public abstract class MelServiceProperties<Service extends IMelService> {
    protected final Service service;

    protected MelServiceProperties(Service service) {
        this.service = service;
    }

    public Service getService() {
        return service;
    }
}
