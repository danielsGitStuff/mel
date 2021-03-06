package de.mel.filesync.gui;


import de.mel.Lok;
import de.mel.filesync.data.conflict.Conflict;
import de.mel.filesync.data.conflict.EmptyRowConflict;
import de.mel.filesync.sql.Stage;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

/**
 * Created by xor on 6/23/17.
 */
public class MergeListCell extends AbstractMergeListCell {

    private final ListView<Conflict> rightList;
    private final ListView<Conflict> leftList;
    private Button btnLeft, btnRight;
    private HBox spacerLeft, spacerRight;

    public MergeListCell(ListView<Conflict> leftList, ListView<Conflict> rightList) {
        this.leftList = leftList;
        this.rightList = rightList;
    }

    @Override
    Stage getConflictSide(Conflict dependsOn) {
        return null;
    }

    @Override
    void handleAction(ActionEvent event) {
        if (lastSelected != null) {
            Lok.debug("AbstractMergeListCell.unsolve " + lastSelected);
            lastSelected.chooseNothing();
            leftList.refresh();
            rightList.refresh();
            getListView().refresh();
            //selectSame(getListView(), leftList, rightList);
        }
    }

    @Override
    void init() {
        btnRight = new Button(">>");
        btnLeft = new Button("<<");
        spacerLeft = new HBox();
        spacerRight = new HBox();
        spacerRight.setMouseTransparent(true);
        spacerLeft.setMouseTransparent(true);
        HBox.setHgrow(spacerLeft, Priority.ALWAYS);
        HBox.setHgrow(spacerRight, Priority.ALWAYS);
        addChildren(vBox, label, lblHash);
        addChildren(hbox, btnLeft, spacerLeft, vBox, spacerRight, btnRight);
    }

    @Override
    protected void updateItem(Conflict conflict, boolean empty) {
        if (conflict instanceof EmptyRowConflict) {
            setBackground(new Background(new BackgroundFill(new Color(.3, .3, .3, 1), CornerRadii.EMPTY, Insets.EMPTY)));
        } else {
            indent = 0;
            if (empty || conflict == null) {
                setGraphic(null);
                lastSelected = null;
            } else {
                lastSelected = conflict;
                if (conflict.hasDecision()) {
                    label.setText(conflict.getChoice().getName());
                    lblHash.setText(conflict.getChoice().getContentHash());
                }
                setGraphic(hbox);
            }
            indent();
        }
    }

    @Override
    boolean isLeft() {
        return false;
    }
}
