package de.mel.filesync.gui;

import de.mel.Lok;
import de.mel.filesync.data.conflict.ConflictRow;
import javafx.scene.control.Button;
import javafx.scene.control.TreeTableCell;

public class MelFXButtonCell extends TreeTableCell<ConflictRow, ConflictRow> {
    private final boolean isLocalCell;
    private Button button;

    public MelFXButtonCell(String text, boolean isLocalCell) {
        this.isLocalCell = isLocalCell;
        this.button = new Button(text);
    }

    @Override
    protected void updateItem(ConflictRow item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
//            Lok.debug("debug empty button");
            setGraphic(null);
        } else {
            ConflictRow conflictRow = this.getTableRow().getTreeItem().getValue();
            boolean D = false;
            if ("sub22".equals(conflictRow.getLocalName())) {
                D = true;
            }
            boolean showButton = (conflictRow != null) && (
                    (this.isLocalCell && !conflictRow.hasChosenLocal()) ||
                            (!this.isLocalCell && !conflictRow.hasChosenRemote()));
            showButton = (conflictRow != null) && (
                    (this.isLocalCell && !conflictRow.hasChosenLocal()) ||
                            (!this.isLocalCell && !conflictRow.hasChosenRemote()));
            if (D)
                Lok.debug("debug Button (" + conflictRow.getLocalName() + ") " + (conflictRow == null) + " " + showButton);
            if (showButton) {
                setGraphic(this.button);
            } else {
                setGraphic(null);
            }
            this.button.onActionProperty().setValue(event -> {
                Lok.debug("choice clicked");
                if (this.isLocalCell) {
                    conflictRow.decideLocal();
                } else {
                    conflictRow.decideRemote();
                }
                this.getTreeTableView().refresh();
            });
        }
    }
}
