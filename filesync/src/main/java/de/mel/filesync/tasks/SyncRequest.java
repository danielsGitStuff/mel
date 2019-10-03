package de.mel.filesync.tasks;


import de.mel.auth.data.ServicePayload;
import de.mel.auth.service.Bootloader;
import de.mel.core.serialize.JsonIgnore;
import de.mel.filesync.sql.StageSet;

/**
 * Created by xor on 10/27/16.
 */
public class SyncRequest extends ServicePayload {
    private long oldVersion;
    private Long newVersion;
    @JsonIgnore
    private Long stageSetId;
    @JsonIgnore
    private boolean retrieveMissingInformation = true;
    private StageSet stageSet;

    public SyncRequest() {
        this.level = Bootloader.BootLevel.LONG;
    }

    public Long getStageSetId() {
        return stageSetId;
    }

    public SyncRequest setStageSetId(Long stageSetId) {
        this.stageSetId = stageSetId;
        return this;
    }

    public SyncRequest setOldVersion(long oldVersion) {
        this.oldVersion = oldVersion;
        return this;
    }

    public SyncRequest setNewVersion(long newVersion) {
        this.newVersion = newVersion;
        return this;
    }

    public Long getNewVersion() {
        return newVersion;
    }

    public SyncRequest setRetrieveMissingInformation(boolean retrieveMissingInformation) {
        this.retrieveMissingInformation = retrieveMissingInformation;
        return this;
    }

    public boolean getRetrieveMissingInformation() {
        return retrieveMissingInformation;
    }

    public long getOldVersion() {
        return oldVersion;
    }


    public void setStageSet(StageSet stageSet) {
        this.stageSet = stageSet;
    }

    public StageSet getStageSet() {
        return stageSet;
    }
}
