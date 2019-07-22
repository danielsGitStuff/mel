package de.mein.drive.data;


import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import de.mein.Lok;
import de.mein.auth.data.cached.CachedList;
import de.mein.auth.service.Bootloader;
import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.drive.sql.GenericFSEntry;
import de.mein.drive.sql.StageSet;

/**
 * Created by xor on 10/27/16.
 */
public class SyncAnswer extends CachedList<GenericFSEntry> {
    private long oldVersion;
    private Long newVersion;
    @JsonIgnore
    private Long stageSetId;
    @JsonIgnore
    private boolean retrieveMissingInformation = true;
    private StageSet stageSet;

    public SyncAnswer(File cacheDir, Long cacheId, int partSize) {
        super(cacheDir, cacheId, partSize);
        this.level = Bootloader.BootLevel.LONG;
    }

    @Override
    public void add(GenericFSEntry elem) throws JsonSerializationException, IllegalAccessException, IOException, NoSuchMethodException, InstantiationException, InvocationTargetException {
        super.add(elem);
    }

    public SyncAnswer() {
        Lok.debug("debug");
        this.level = Bootloader.BootLevel.LONG;
    }

    public Long getStageSetId() {
        return stageSetId;
    }

    public SyncAnswer setStageSetId(Long stageSetId) {
        this.stageSetId = stageSetId;
        return this;
    }

    public SyncAnswer setOldVersion(long oldVersion) {
        this.oldVersion = oldVersion;
        return this;
    }

    public SyncAnswer setNewVersion(long newVersion) {
        this.newVersion = newVersion;
        return this;
    }

    public Long getNewVersion() {
        return newVersion;
    }

    public SyncAnswer setRetrieveMissingInformation(boolean retrieveMissingInformation) {
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
