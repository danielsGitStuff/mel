package de.mein.drive.tasks;


import de.mein.auth.data.IPayload;
import de.mein.core.serialize.JsonIgnore;
import de.mein.drive.sql.GenericFSEntry;
import de.mein.drive.sql.StageSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 10/27/16.
 */
public class SyncTask implements IPayload {
    private long version;
    private List<GenericFSEntry> result = new ArrayList<>();
    @JsonIgnore
    private Long sourceCertId;
    @JsonIgnore
    private String sourceServiceUuid;
    @JsonIgnore
    private Long stageSetId;
    @JsonIgnore
    private boolean retrieveMissingInformation = true;
    private StageSet stageSet;

    public Long getStageSetId() {
        return stageSetId;
    }

    public SyncTask setStageSetId(Long stageSetId) {
        this.stageSetId = stageSetId;
        return this;
    }

    public SyncTask setVersion(long version) {
        this.version = version;
        return this;
    }

    public SyncTask setRetrieveMissingInformation(boolean retrieveMissingInformation) {
        this.retrieveMissingInformation = retrieveMissingInformation;
        return this;
    }

    public boolean getRetrieveMissingInformation() {
        return retrieveMissingInformation;
    }

    public long getVersion() {
        return version;
    }

    public SyncTask setResult(List<GenericFSEntry> result) {
        this.result = result;
        return this;
    }

    public List<GenericFSEntry> getResult() {
        return result;
    }

    public void setSourceCertId(Long sourceCertId) {
        this.sourceCertId = sourceCertId;
    }

    public void setSourceServiceUuid(String sourceServiceUuid) {
        this.sourceServiceUuid = sourceServiceUuid;
    }

    public Long getSourceCertId() {
        return sourceCertId;
    }

    public String getSourceServiceUuid() {
        return sourceServiceUuid;
    }

    public void setStageSet(StageSet stageSet) {
        this.stageSet = stageSet;
    }

    public StageSet getStageSet() {
        return stageSet;
    }
}
