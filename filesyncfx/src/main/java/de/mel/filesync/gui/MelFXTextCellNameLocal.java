package de.mel.filesync.gui;

import de.mel.filesync.data.conflict.ConflictRow;
import javafx.scene.paint.Color;

public class MelFXTextCellNameLocal extends MelFXTextCellAbstract {


    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
    }

    @Override
    String getRowText(ConflictRow row) {
        if (row != null)
            return row.getLocalName();
        return ConflictRow.NAME_EMPTY;
    }

    @Override
    Color getRowBackgroundColour(ConflictRow row) {
        return MelFXTextCellBackground.COLOUR_LOCAL;
    }
}
