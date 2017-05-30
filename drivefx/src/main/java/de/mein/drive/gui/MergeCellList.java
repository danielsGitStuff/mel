package de.mein.drive.gui;

import de.mein.drive.service.sync.Conflict;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;

/**
 * Created by xor on 5/30/17.
 */
public class MergeCellList extends ListCell<Conflict> {
    private Button actionBtn;
    protected Label name ;
    private GridPane pane ;
    public MergeCellList(){
        super();
        actionBtn = new Button("my action");
        actionBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                System.out.println("Action: "+getItem());
            }
        });
        name = new Label();
        pane = new GridPane();
        pane.add(name, 0, 0);
        pane.add(actionBtn, 0, 1);
        setText(null);
    }
}
