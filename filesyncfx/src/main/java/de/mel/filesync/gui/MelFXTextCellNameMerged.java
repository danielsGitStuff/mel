package de.mel.filesync.gui;

import de.mel.filesync.data.conflict.Conflict;
import de.mel.filesync.data.conflict.ConflictRow;

import java.util.Objects;

public class MelFXTextCellNameMerged extends MelFXTextCellBackground<ConflictRow, ConflictRow> {
    @Override
    protected void updateItem(ConflictRow item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            this.setText(null);
            this.setBackground(MelFXTextCellBackground.COLOUR_NULL);
        } else {
            ConflictRow conflictRow = this.getItem();
            if (conflictRow.hasFsEntry()) {
                this.setText(conflictRow.getFsEntry().getName().v());
            } else if (conflictRow.hasStage()) {
                this.setText(conflictRow.getStage().getName());
            } else {
                if (!conflictRow.hasChoice()) {
                    Conflict c = conflictRow.getConflict();
                    if (c.hasLocalStage() && c.hasRemoteStage() && c.getLocalStage().getIsDirectory() && c.getRemoteStage().getIsDirectory() && Objects.equals(c.getLocalStage().getName(), c.getRemoteStage().getName()))
                        this.setText(c.getLocalStage().getName());
                    else
                        this.setText("?");
                } else if (conflictRow.hasChosenLocal()) {
                    this.setText(conflictRow.getLocalName());
                    this.setBackground(MelFXTextCellBackground.COLOUR_LOCAL);
                } else {
                    this.setText(conflictRow.getRemoteName());
                    this.setBackground(MelFXTextCellBackground.COLOUR_REMOTE);
                }
            }
        }
    }
}
