package de.mein.drive.gui;

import de.mein.auth.gui.PopupContentFX;
import de.mein.auth.service.MeinService;
import de.mein.drive.jobs.CommitJob;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.service.sync.conflict.Conflict;
import de.mein.drive.service.sync.conflict.ConflictException;
import de.mein.drive.service.sync.conflict.ConflictSolver;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.util.Callback;

import java.net.URL;
import java.util.ResourceBundle;


/**
 * Created by xor on 5/30/17.
 */
@SuppressWarnings("Duplicates")
public class DriveFXConflictSolverController implements PopupContentFX, Initializable {
    @FXML
    private TreeTableView<Conflict> treeTableView;
    @FXML
    private TreeTableColumn<Conflict, String> colLeft, colMerged, colRight;
    private MeinDriveClientService meinDriveClientService;
    private ConflictSolver conflictSolver;


    @Override
    public String onOkCLicked() {
        System.out.println("DriveFXConflictSolverController.onOkCLicked");
        try {
            conflictSolver.isSolved();
            meinDriveClientService.addJob(new CommitJob());
            return null;
        } catch (ConflictException e) {
            return "you bloody idiot! you caused an " + e.getClass().getSimpleName();

        }
    }

    @Override
    public void init(MeinService meinService, Object msgObject) {
        System.out.println("DriveFXConflictSolverController.init");
        this.meinDriveClientService = (MeinDriveClientService) meinService;
        conflictSolver = (ConflictSolver) msgObject;
        meinDriveClientService.addConflictSolver(conflictSolver);
        TreeItem<Conflict> root = new TreeItem<>(new Conflict());
        treeTableView.setRoot(root);

        colLeft.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getKey()));
        colRight.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getKey()));
        colLeft.setCellFactory(new Callback<TreeTableColumn<Conflict, String>, TreeTableCell<Conflict, String>>() {
            @Override
            public TreeTableCell<Conflict, String> call(TreeTableColumn<Conflict, String> param) {
                TreeConflictCell cell = new TreeConflictCell();
                return cell;
            }
        });

        for (Conflict conflict : conflictSolver.getConflicts()) {
            root.getChildren().add(new TreeItem<>(conflict));
        }
        System.out.println("DriveFXConflictSolverController.init.done");
    }


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        colLeft.prefWidthProperty().bind(treeTableView.widthProperty().divide(3));
        colMerged.prefWidthProperty().bind(treeTableView.widthProperty().divide(3));
        colRight.prefWidthProperty().bind(treeTableView.widthProperty().divide(3));
        treeTableView.setShowRoot(false);
    }
}
