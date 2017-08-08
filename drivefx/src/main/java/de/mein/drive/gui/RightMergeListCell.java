package de.mein.drive.gui;

import de.mein.drive.data.conflict.Conflict;
import de.mein.drive.sql.Stage;
import javafx.event.ActionEvent;
import javafx.scene.control.ListView;

/**
 * Created by xor on 6/22/17.
 */
public class RightMergeListCell extends AbstractMergeListCell {

    private final ListView<Conflict> leftList;
    private final ListView<Conflict> mergeList;

    public RightMergeListCell(ListView<Conflict> leftList, ListView<Conflict> mergeList) {
        this.mergeList = mergeList;
        this.leftList = leftList;
    }


    @Override
    void handleAction(ActionEvent event) {
        if (lastSelected != null) {
            System.out.println("AbstractMergeListCell.left " + lastSelected);
            lastSelected.chooseRight();
            getListView().refresh();
            mergeList.refresh();
            leftList.refresh();
            //selectSame(getListView(), leftList, mergeList);
        }
    }

    @Override
    void init() {
        button.setText("<<");
        addChildren(button, spacer, label, indentSpacer);
    }

    @Override
    Stage getConflictSide(Conflict dependsOn) {
        if (dependsOn.hasRight())
            return dependsOn.getRight();
        return null;
    }

    @Override
    boolean isLeft() {
        return false;
    }


}
