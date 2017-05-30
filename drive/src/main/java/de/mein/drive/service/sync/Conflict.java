package de.mein.drive.service.sync;

import de.mein.drive.sql.Stage;

/**
 * Created by xor on 5/30/17.
 */
public class Conflict {

    private final Stage lStage, rStage;
    private Boolean isRight;
    private final String key;

    public Conflict(Stage lStage, Stage rStage) {
        this.lStage = lStage;
        this.rStage = rStage;
        key = createKey(lStage, rStage);
    }

    public static String createKey(Stage lStage, Stage rStage) {
        return lStage.getId() + "/" + rStage.getId();
    }

    public void chooseRight() {
        isRight = true;
    }

    public void chooseLeft() {
        isRight = false;
    }

    public boolean isRight() {
        return isRight;
    }

    public Stage getChoice() {
        return isRight ? rStage : lStage;
    }

    public boolean hasDecision() {
        return isRight != null;
    }

    public String getKey() {
        return key;
    }
}
