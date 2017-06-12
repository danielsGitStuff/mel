package de.mein.drive.gui;

import de.mein.drive.service.sync.Conflict;
import javafx.scene.control.TreeTableCell;

/**
 * Created by xor on 6/12/17.
 */
public class TreeConflictCell extends TreeTableCell<Conflict, String> {
    private Conflict conflict;
    private Conflict lastSelected;

    public TreeConflictCell() {
        setText("nix");
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            return;
        }
    }
}
