package de.mel.filesync.data;

import de.mel.auth.data.ServicePayload;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by xor on 1/14/17.
 */
public class CommitAnswer extends ServicePayload {
private Map<Long, Long> stageIdFsIdMap = new HashMap<>();
    /**
     * the version the server is at with the commit (null from servers that do not say)
     */
    private Long newVersion;

    public Long getNewVersion() {
        return newVersion;
    }

    public CommitAnswer setNewVersion(Long newVersion) {
        this.newVersion = newVersion;
        return this;
    }


    public CommitAnswer setStageIdFsIdMap(Map<Long, Long> stageIdFsIdMap) {
        this.stageIdFsIdMap = stageIdFsIdMap;
        return this;
    }

    public Map<Long, Long> getStageIdFsIdMap() {
        return stageIdFsIdMap;
    }

}
