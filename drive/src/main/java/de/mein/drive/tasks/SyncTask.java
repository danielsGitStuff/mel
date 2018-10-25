package de.mein.drive.tasks;


import de.mein.Lok;
import de.mein.auth.data.IPayload;
import de.mein.core.serialize.JsonIgnore;
import de.mein.auth.data.cached.CachedIterable;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.drive.sql.GenericFSEntry;
import de.mein.drive.sql.StageSet;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by xor on 10/27/16.
 */
public class SyncTask extends CachedIterable<GenericFSEntry> implements IPayload {
    private long oldVersion;
    private Long newVersion;
    @JsonIgnore
    private Long sourceCertId;
    @JsonIgnore
    private String sourceServiceUuid;
    @JsonIgnore
    private Long stageSetId;
    @JsonIgnore
    private boolean retrieveMissingInformation = true;
    private StageSet stageSet;

    public SyncTask(File cacheDir, int partSize) {
        super(cacheDir, partSize);
    }

    @Override
    public void add(GenericFSEntry elem) throws JsonSerializationException, IllegalAccessException, IOException, NoSuchMethodException, InstantiationException, InvocationTargetException {
        super.add(elem);
    }

    public SyncTask() {
        Lok.debug("debug");
    }

    public Long getStageSetId() {
        return stageSetId;
    }

    public SyncTask setStageSetId(Long stageSetId) {
        this.stageSetId = stageSetId;
        return this;
    }

    public SyncTask setOldVersion(long oldVersion) {
        this.oldVersion = oldVersion;
        return this;
    }

    public SyncTask setNewVersion(long newVersion) {
        this.newVersion = newVersion;
        return this;
    }

    public Long getNewVersion() {
        return newVersion;
    }

    public SyncTask setRetrieveMissingInformation(boolean retrieveMissingInformation) {
        this.retrieveMissingInformation = retrieveMissingInformation;
        return this;
    }

    public boolean getRetrieveMissingInformation() {
        return retrieveMissingInformation;
    }

    public long getOldVersion() {
        return oldVersion;
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
