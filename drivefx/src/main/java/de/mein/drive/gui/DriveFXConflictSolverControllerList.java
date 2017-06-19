package de.mein.drive.gui;

import de.mein.auth.gui.PopupContentFX;
import de.mein.auth.service.MeinService;
import de.mein.drive.jobs.CommitJob;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.service.sync.Conflict;
import de.mein.drive.service.sync.ConflictSolver;
import javafx.fxml.FXML;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;


/**
 * Created by xor on 5/30/17.
 */
public class DriveFXConflictSolverControllerList implements PopupContentFX {
    @FXML
    private ListView<Conflict> listLeft, listRight, listMerge;
    private MeinDriveClientService meinDriveClientService;
    private ConflictSolver conflictSolver;


    @Override
    public String onOkCLicked() {
        System.out.println("DriveFXConflictSolverController.onOkCLicked");
        if (conflictSolver.isSolved()) {
            meinDriveClientService.addJob(new CommitJob());
            return null;
        }
        return "you bloody idiot!";
    }

    @Override
    public void init(MeinService meinService, Object msgObject) {
        this.meinDriveClientService = (MeinDriveClientService) meinService;
        conflictSolver = (ConflictSolver) msgObject;
        meinDriveClientService.addConflictSolver(conflictSolver);
        System.out.println("DriveFXConflictSolverController.init");
        MergeListCell.setup(listLeft, listMerge, listRight);
        listLeft.getItems().addAll(conflictSolver.getConflicts());
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
