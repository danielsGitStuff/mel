package de.mel.filesync.gui;

import de.mel.filesync.data.conflict.Conflict;
import de.mel.filesync.sql.Stage;
import javafx.scene.control.TreeTableCell;

/**
 * Created by xor on 6/12/17.
 */
public class TreeViewStageCell extends TreeTableCell<Conflict, TreeViewStage> {
    private Conflict conflict;
    private Conflict lastSelected;

    public TreeViewStageCell() {
    }

    @Override
    protected void updateItem(TreeViewStage stage, boolean empty) {
        super.updateItem(stage, empty);
        if (empty || stage == null) {
            setText(null);
        } else {
            setText(stage.name.getValue());
//            setText("bla");
        }
    }
}
