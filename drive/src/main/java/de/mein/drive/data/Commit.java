package de.mein.drive.data;

import de.mein.auth.data.IPayload;
import de.mein.auth.data.cached.data.CachedIterable;
import de.mein.drive.sql.Stage;

import java.io.File;

/**
 * Created by xor on 1/14/17.
 */
public class Commit extends CachedIterable<Stage> implements IPayload{
    private String serviceUuid;
    private Long basedOnVersion;

    public Commit(){
        System.out.println("Commit.Commit");
    }

    public Commit(File cacheDir, int partSize) {
        super(cacheDir,partSize);
    }

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
