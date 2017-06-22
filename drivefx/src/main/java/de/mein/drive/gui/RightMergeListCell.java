package de.mein.drive.gui;

import de.mein.drive.service.sync.Conflict;
import javafx.event.ActionEvent;
import javafx.scene.control.ListView;

/**
 * Created by xor on 6/22/17.
 */
public class RightMergeListCell extends MergeListCell {

    private final ListView<Conflict> leftList;
    private final ListView<Conflict> mergeList;

    public RightMergeListCell(ListView<Conflict> leftList, ListView<Conflict> mergeList) {
        this.mergeList = mergeList;
        this.leftList = leftList;
    }


    @Override
    void handleAction(ActionEvent event) {
        if (lastSelected != null) {
            System.out.println("MergeListCell.left " + lastSelected);
            lastSelected.chooseRight();
            getListView().refresh();
            mergeList.refresh();
            leftList.refresh();
            selectSame(getListView(), leftList, mergeList);
        }
    }

    @Override
    void init() {
        button.setText("<<");
        addChildren(button, spacer, label, indentSpacer);
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
                if (dependsOn.hasRight() && dependsOn.getRight().getDeleted())
                    parentDeleted = true;
                dependsOn = dependsOn.getDependsOn();
            }
            if (parentDeleted || (conflict.hasRight() && conflict.getRight().getDeleted()))
                setBackground(createDeletedBackground());
            else
                setBackground(createDefaultdBackground());
            if (!conflict.hasDecision()) {
                if (conflict.hasRight()) {
                    label.setText(conflict.getRight().getName());
                } else if (parentDeleted)
                    label.setText("<parent deleted>");
                setGraphic(hbox);
            } else if (conflict.isRight()){
                label.setText("");
                button.setVisible(false);
                setBackground(null);            }
        }
        indent();
    }
}
