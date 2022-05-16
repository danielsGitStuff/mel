package de.mel.filesync.gui;

import de.mel.filesync.data.conflict.ConflictRow;
import javafx.scene.paint.Color;

public class MelFXTextCellRemote extends MelFXTextCellAbstract {
    @Override
    String getRowText(ConflictRow row) {
        if (row != null)
            return row.getRemoteName();
        return ConflictRow.NAME_EMPTY;
    }

    @Override
    Color getRowBackgroundColour(ConflictRow row) {
        return MelFXTextCellBackground.COLOUR_REMOTE;
    }
}
