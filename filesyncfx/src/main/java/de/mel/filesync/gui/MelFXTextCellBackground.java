package de.mel.filesync.gui;

import de.mel.filesync.data.conflict.ConflictRow;
import javafx.geometry.Insets;
import javafx.scene.control.TreeTableCell;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;

public class MelFXTextCellBackground<S, T> extends TreeTableCell<S, T> {
    public static Color COLOUR_LOCAL = Color.rgb(255, 63, 65, 0.2);
    public static Color COLOUR_REMOTE = Color.rgb(60, 255, 65, 0.2);
    public static Color COLOUR_NULL = null;

    protected void setBackground(Color colour) {
        this.backgroundProperty().setValue(new Background(new BackgroundFill(colour, CornerRadii.EMPTY, Insets.EMPTY)));
    }

}
