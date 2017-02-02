package de.mein.drive.data;

import de.mein.auth.data.IPayload;
import de.mein.drive.sql.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 1/14/17.
 */
public class Commit implements IPayload {
    private List<Stage> stages = new ArrayList<>();
    private String serviceUuid;

    public List<Stage> getStages() {
        return stages;
    }

    public Commit setStages(List<Stage> stages) {
        this.stages = stages;
        return this;
    }

    public Commit setServiceUuid(String serviceUuid) {
        this.serviceUuid = serviceUuid;
        return this;
    }

    public String getServiceUuid() {
        return serviceUuid;
    }
}
