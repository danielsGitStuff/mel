package de.mein.drive.gui;

import de.mein.Lok;
import de.mein.auth.MeinNotification;
import de.mein.auth.gui.PopupContentFX;
import de.mein.auth.service.MeinAuthService;
import de.mein.drive.data.conflict.Conflict;
import de.mein.drive.data.conflict.ConflictSolver;
import de.mein.drive.jobs.CommitJob;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.sql.Stage;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.util.Callback;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;


/**
 * Created by xor on 5/30/17.
 */
@SuppressWarnings("Duplicates")
public class DriveFXConflictSolverController extends PopupContentFX implements Initializable {
    @FXML
    private TreeTableView<Conflict> treeTableView;
    @FXML
    private TreeTableColumn<Conflict, String> colMerged, colRight;
    @FXML
    private TreeTableColumn<Conflict, Stage> colLeft;
    private MeinDriveClientService meinDriveClientService;
    private ConflictSolver conflictSolver;


    @Override
    public String onOkCLicked() {
        Lok.debug("DriveFXConflictSolverController.onOkCLicked");
        conflictSolver.isSolved();
        meinDriveClientService.addJob(new CommitJob());
        return null;
    }

    @Override
    public void initImpl(javafx.stage.Stage stage, MeinAuthService meinAuthService, MeinNotification notification) {
        Lok.debug("DriveFXConflictSolverController.init");
        this.meinDriveClientService = (MeinDriveClientService) meinAuthService.getMeinService(notification.getServiceUuid());
        this.stage.setTitle(notification.getTitle());
        for (ConflictSolver conflictSolver : meinDriveClientService.getConflictSolverMap().values()) {
            if (conflictSolver.hasConflicts() && !conflictSolver.isSolved()) {
                this.conflictSolver = conflictSolver;
                List<Conflict> rootConflicts = Conflict.getRootConflicts(conflictSolver.getConflicts());


                TreeItem<Conflict> root = new TreeItem<>(new Conflict());
                treeTableView.setRoot(root);

                colLeft.setCellValueFactory(new Callback<TreeTableColumn.CellDataFeatures<Conflict, Stage>, ObservableValue<Stage>>() {
                    @Override
                    public ObservableValue<Stage> call(TreeTableColumn.CellDataFeatures<Conflict, Stage> param) {
                        return null;
                    }
                });
                //colLeft.setCellValueFactory(new PropertyValueFactory<Conflict,Stage>("hurr"));
                //colLeft.setCellValueFactory(param -> param.getValue().getValue().getLeft());
                colRight.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getKey()));
                colLeft.setCellFactory(new Callback<TreeTableColumn<Conflict, Stage>, TreeTableCell<Conflict, Stage>>() {
                    @Override
                    public TreeTableCell<Conflict, Stage> call(TreeTableColumn<Conflict, Stage> param) {
                        return new TreeConflictCell();
                    }
                });

//                colLeft.setCellFactory(new Callback<TreeTableColumn<Conflict, String>, TreeTableCell<Conflict, String>>() {
//                    @Override
//                    public TreeTableCell<Conflict, String> call(TreeTableColumn<Conflict, String> param) {
//                        TreeConflictCell cell = new TreeConflictCell();
//                        return cell;
//                    }
//                });

                for (Conflict conflict : conflictSolver.getConflicts()) {
                    root.getChildren().add(new TreeItem<>(conflict));
                }


                //stop after the first one. we can only show one Activity anyway.
                break;
            }

            conflictSolver = (ConflictSolver) notification.getContent();

            Lok.debug("DriveFXConflictSolverController.init.done");
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        colLeft.prefWidthProperty().bind(treeTableView.widthProperty().divide(3));
        colMerged.prefWidthProperty().bind(treeTableView.widthProperty().divide(3));
        colRight.prefWidthProperty().bind(treeTableView.widthProperty().divide(3));
        treeTableView.setShowRoot(false);
    }
}
