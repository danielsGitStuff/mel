package de.mein.drive.gui;

import de.mein.drive.data.conflict.Conflict;
import de.mein.drive.sql.Stage;
import javafx.scene.control.TreeTableCell;

/**
 * Created by xor on 6/12/17.
 */
public class TreeConflictCell extends TreeTableCell<Conflict, Stage> {
    private Conflict conflict;
    private Conflict lastSelected;

    public TreeConflictCell() {
    }

    @Override
    protected void updateItem(Stage stage, boolean empty) {
        super.updateItem(stage, empty);
        if (empty || stage == null) {
            setText(null);
        } else {
            setText(stage.getName());
        }
    }
}
