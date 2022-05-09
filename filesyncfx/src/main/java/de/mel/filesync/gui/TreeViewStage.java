package de.mel.filesync.gui;

import de.mel.filesync.sql.Stage;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class TreeViewStage {
    private final Stage stage;
    public StringProperty name;

    public TreeViewStage(Stage stage) {
        this.stage = stage;
        this.name = new SimpleStringProperty(stage.getName());
    }
}
