package de.mel.filesync.gui;

import de.mel.filesync.data.conflict.ConflictRow;
import javafx.geometry.Insets;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;

public abstract class MelFXTextCellAbstract extends MelFXTextCellBackground<ConflictRow, String> {

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            this.textProperty().set(null);
            this.backgroundProperty().set(null);
        } else {
            ConflictRow conflictRow = this.getTableRow().getItem();
            this.textProperty().set(this.getRowText(conflictRow));
            if (conflictRow == null) {
                this.setBackground(Color.WHITE);
            } else {
                this.setBackground(this.getRowBackgroundColour(conflictRow));
            }
//            Lok.debug("debug ftgf " + (conflictRow == null));
        }
//        Lok.debug("debug text (local.name) update");
    }

    abstract Color getRowBackgroundColour(ConflictRow row);


    abstract String getRowText(ConflictRow row);

}
