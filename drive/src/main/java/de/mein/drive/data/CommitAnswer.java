package de.mein.drive.data;

import de.mein.auth.data.IPayload;
import de.mein.drive.sql.GenericFSEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by xor on 1/14/17.
 */
public class CommitAnswer implements IPayload {
    private List<GenericFSEntry> delta;
private Map<Long, Long> stageIdFsIdMao = new HashMap<>();

    public CommitAnswer setDelta(List<GenericFSEntry> delta) {
        this.delta = delta;
        return this;
    }

    public CommitAnswer setStageIdFsIdMao(Map<Long, Long> stageIdFsIdMao) {
        this.stageIdFsIdMao = stageIdFsIdMao;
        return this;
    }

    public Map<Long, Long> getStageIdFsIdMao() {
        return stageIdFsIdMao;
    }

    public List<GenericFSEntry> getDelta() {
        return delta;
    }
}
