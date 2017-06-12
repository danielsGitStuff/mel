package de.mein.drive.service.sync;

import de.mein.drive.sql.Stage;
import de.mein.drive.sql.dao.StageDao;
import de.mein.sql.SqlQueriesException;

/**
 * Created by xor on 5/30/17.
 */
public class Conflict {

    private Long lStageId, rStageId;
    private StageDao stageDao;
    private Boolean isRight;
    private String key;

    public Conflict(StageDao stageDao, Stage lStage, Stage rStage) {
        this.lStageId = lStage.getId();
        this.rStageId = rStage.getId();
        this.stageDao = stageDao;
        key = createKey(lStage, rStage);
    }

    public Conflict() {

    }

    public static String createKey(Stage lStage, Stage rStage) {
        return lStage.getId() + "/" + rStage.getId();
    }

    public Conflict chooseRight() {
        isRight = true;
        return this;
    }

    public Conflict chooseLeft() {
        isRight = false;
        return this;
    }

    public boolean isRight() {
        return isRight != null && isRight;
    }

    public Stage getChoice() throws SqlQueriesException {
        return isRight ? getRight() : getLeft();
    }

    public boolean hasDecision() {
        return isRight != null;
    }

    public String getKey() {
        return key;
    }

    public Stage getLeft() throws SqlQueriesException {
        return stageDao.getStageById(lStageId);
    }

    public Stage getRight() throws SqlQueriesException {
        return stageDao.getStageById(rStageId);
    }

    public void chooseNothing() {
        isRight = null;
    }

    public boolean isLeft() {
        return isRight == null || !isRight;
    }
}
