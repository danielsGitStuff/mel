package de.mein.drive.data;

import de.mein.auth.data.IPayload;
import de.mein.core.serialize.data.CachedData;
import de.mein.core.serialize.data.CachedIterable;
import de.mein.drive.sql.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 1/14/17.
 */
public class Commit extends CachedIterable<Stage> implements IPayload{
    private String serviceUuid;
    private Long basedOnVersion;

    public Commit setBasedOnVersion(Long basedOnVersion) {
        this.basedOnVersion = basedOnVersion;
        return this;
    }

    public Long getBasedOnVersion() {
        return basedOnVersion;
    }

    public Commit setServiceUuid(String serviceUuid) {
        this.serviceUuid = serviceUuid;
        return this;
    }

    public String getServiceUuid() {
        return serviceUuid;
    }

}
