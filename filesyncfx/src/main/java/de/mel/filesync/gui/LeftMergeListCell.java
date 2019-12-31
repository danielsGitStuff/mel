package de.mel.filesync.gui;

import de.mel.Lok;
import de.mel.filesync.data.conflict.Conflict;
import de.mel.filesync.sql.Stage;
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
            Lok.debug("AbstractMergeListCell.left " + lastSelected);
            lastSelected.decideLocal();
            getListView().refresh();
            mergeList.refresh();
            rightList.refresh();
            //selectSame(getListView(), mergeList, rightList);
        }
    }

    @Override
    void init() {

        button.setText(">>");
        addChildren(vBox,label,lblHash);
        addChildren(hbox,indentSpacer, vBox, spacer, button);
    }

    @Override
    Stage getConflictSide(Conflict dependsOn) {
        if (dependsOn.getChosenLocal())
            return dependsOn.getLocalStage();
        return null;
    }

    @Override
    boolean isLeft() {
        return true;
    }


}
