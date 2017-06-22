package de.mein.drive.gui;

import de.mein.drive.service.sync.Conflict;
import javafx.event.ActionEvent;
import javafx.scene.control.ListView;

/**
 * Created by xor on 6/22/17.
 */
@SuppressWarnings("Duplicates")
public class LeftMergeListCell extends MergeListCell {
    private final ListView<Conflict> rightList;
    private final ListView<Conflict> mergeList;

    public LeftMergeListCell(ListView<Conflict> mergeList, ListView<Conflict> rightList) {
        this.mergeList = mergeList;
        this.rightList = rightList;
    }

    @Override
    void handleAction(ActionEvent event) {
        if (lastSelected != null) {
            System.out.println("MergeListCell.left " + lastSelected);
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
    protected void updateItemImpl(Conflict conflict, boolean empty) {
        indent = 0;
        if (empty || conflict == null) {
            setGraphic(null);
            lastSelected = null;
        } else {
            lastSelected = conflict;
            boolean parentDeleted = false;
            Conflict dependsOn = conflict.getDependsOn();
            while (dependsOn != null) {
                indent += 10;
                if (dependsOn.getLeft() != null && dependsOn.getLeft().getDeleted())
                    parentDeleted = true;
                dependsOn = dependsOn.getDependsOn();
            }
            if (parentDeleted || (conflict.hasLeft() && conflict.getLeft().getDeleted()))
                setBackground(createDeletedBackground());
            else
                setBackground(createDefaultdBackground());
            if (!conflict.hasDecision()) {
                if (conflict.hasLeft()) {
                    label.setText(conflict.getLeft().getName());
                } else if (parentDeleted)
                    label.setText("<parent deleted>");
                setGraphic(hbox);
            } else if (conflict.isLeft()){
                label.setText("");
                button.setVisible(false);
                setBackground(null);
            }
        }
        indent();
    }


}
