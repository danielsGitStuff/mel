package de.mein.drive.gui;

import de.mein.drive.service.sync.Conflict;
import de.mein.drive.service.sync.EmptyRowConflict;
import de.mein.drive.sql.Stage;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ListView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;

/**
 * Created by xor on 6/23/17.
 */
public class MergeListCell extends AbstractMergeListCell {

    private final ListView<Conflict> rightList;
    private final ListView<Conflict> leftList;

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
            System.out.println("AbstractMergeListCell.unsolve " + lastSelected);
            lastSelected.chooseNothing();
            leftList.refresh();
            rightList.refresh();
            getListView().refresh();
            selectSame(getListView(), leftList, rightList);
        }
    }

    @Override
    void init() {
        addChildren(button);
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
                if (conflict.hasDecision())
                    button.setText(conflict.getChoice().getName());
            }
            indent();
        }
    }

    @Override
    boolean isLeft() {
        return false;
    }
}
