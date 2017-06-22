package de.mein.drive.gui;

import de.mein.drive.service.sync.Conflict;
import de.mein.drive.sql.Stage;
import javafx.event.ActionEvent;
import javafx.scene.control.ListView;

/**
 * Created by xor on 6/22/17.
 */
@SuppressWarnings("Duplicates")
public class LeftMergeListCell extends AbstractMergeListCell {
    private final ListView<Conflict> rightList;
    private final ListView<Conflict> mergeList;

    public LeftMergeListCell(ListView<Conflict> mergeList, ListView<Conflict> rightList) {
        this.mergeList = mergeList;
        this.rightList = rightList;
    }

    @Override
    void handleAction(ActionEvent event) {
        if (lastSelected != null) {
            System.out.println("AbstractMergeListCell.left " + lastSelected);
            lastSelected.chooseLeft();
            getListView().refresh();
            mergeList.refresh();
            rightList.refresh();
            selectSame(getListView(), mergeList, rightList);
        }
    }

    @Override
    void init() {
        button.setText(">>");
        addChildren(indentSpacer, label, spacer, button);
    }

    @Override
    Stage getConflictSide(Conflict dependsOn) {
        if (dependsOn.hasLeft())
            return dependsOn.getLeft();
        return null;
    }


}
