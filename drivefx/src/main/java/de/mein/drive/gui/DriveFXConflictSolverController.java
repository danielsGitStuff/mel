package de.mein.drive.gui;

import de.mein.auth.gui.PopupContentFX;
import de.mein.auth.service.MeinService;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.service.sync.Conflict;
import de.mein.drive.service.sync.ConflictCollection;
import de.mein.drive.service.sync.ConflictSolver;
import javafx.fxml.FXML;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;


/**
 * Created by xor on 5/30/17.
 */
public class DriveFXConflictSolverController implements ConflictSolver, PopupContentFX {
    @FXML
    private ListView<Conflict> listLeft, listRight, listMerge;
    private MeinDriveClientService meinDriveClientService;
    private ConflictCollection conflictCollection;

    @Override
    public boolean onOkCLicked() {
        System.out.println("DriveFXConflictSolverController.onOkCLicked");
        return false;
    }

    @Override
    public void init(MeinService meinService, Object msgObject) {
        this.meinDriveClientService = (MeinDriveClientService) meinService;
        conflictCollection = (ConflictCollection) msgObject;
        meinDriveClientService.addConflictSolver(this);
        System.out.println("DriveFXConflictSolverController.init");
        listLeft.setCellFactory(leftCellFactory);
        listRight.setCellFactory(rightCellFactory);
        listRight.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            listLeft.getSelectionModel().select((Integer) newValue);
        });
        listLeft.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            listRight.getSelectionModel().select((Integer) newValue);
        });
        listMerge.setCellFactory(MergeListCell.createMergeCellFactory(listLeft, listRight));
        listLeft.getItems().addAll(conflictCollection.getConflicts());
        listRight.getItems().addAll(conflictCollection.getConflicts());
        listMerge.getItems().addAll(conflictCollection.getConflicts());
    }


    private static abstract class GenCallback implements Callback<ListView<Conflict>, ListCell<Conflict>> {

        @Override
        public ListCell<Conflict> call(ListView<Conflict> param) {
            return new ListCell<Conflict>() {
                @Override
                protected void updateItem(Conflict conflict, boolean empty) {
                    super.updateItem(conflict, empty);
                    GenCallback.this.updateItem(this, conflict, empty);
                }
            };
        }

        abstract void updateItem(ListCell<Conflict> listCell, Conflict item, boolean empty);

    }

    private GenCallback leftCellFactory = new GenCallback() {
        @Override
        void updateItem(ListCell<Conflict> listCell, Conflict conflict, boolean empty) {
            if (empty || conflict == null) {
                listCell.setText(null);
            } else {
                String text = conflict.getLeft().getName();
                if (conflict.getLeft().getIsDirectory()) ;
                text += " [D]";
                listCell.setText(text);
            }
        }
    };

    private GenCallback rightCellFactory = new GenCallback() {
        @Override
        void updateItem(ListCell<Conflict> listCell, Conflict conflict, boolean empty) {
            if (empty || conflict == null) {
                listCell.setText(null);
            } else {
                String text = conflict.getRight().getName();
                if (conflict.getRight().getIsDirectory()) ;
                text += " [D]";
                listCell.setText(text);
            }
        }
    };


}
