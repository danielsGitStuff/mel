package de.mel.filesync.gui;

import de.mel.Lok;
import de.mel.auth.MelNotification;
import de.mel.auth.gui.PopupContentFX;
import de.mel.auth.service.MelAuthServiceImpl;
import de.mel.auth.tools.N;
import de.mel.filesync.data.conflict.Conflict;
import de.mel.filesync.data.conflict.ConflictSolver;
import de.mel.filesync.jobs.CommitJob;
import de.mel.filesync.service.MelFileSyncClientService;
import de.mel.filesync.sql.dao.ConflictDao;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;

import java.net.URL;
import java.util.Collection;
import java.util.ResourceBundle;


/**
 * Created by xor on 5/30/17.
 */
@SuppressWarnings("Duplicates")
public class FileSyncFXConflictSolverController extends PopupContentFX implements Initializable {
    @FXML
    private TreeTableView<Conflict> treeTableView;
    @FXML
    private TreeTableColumn<Conflict, String> colMerged, colMergedName, colMergedHash, colRemote, colRemoteName, colRemoteHash;
    @FXML
    private TreeTableColumn<Conflict, String> colLocal, colLocalName, colLocalHash;
    private MelFileSyncClientService melFileSyncClientService;
    private ConflictSolver conflictSolver;
    private ConflictDao conflictDao;

    public static ReadOnlyStringWrapper wrapString(String s) {
        if (s == null)
            return new ReadOnlyStringWrapper("NULL");
        return new ReadOnlyStringWrapper(s);
    }

    @Override
    public String onOkCLicked() {
        Lok.debug("FileSyncFXConflictSolverController.onOkCLicked");
        conflictSolver.isSolved();
        melFileSyncClientService.addJob(new CommitJob());
        return null;
    }

    @Override
    public void initImpl(javafx.stage.Stage stage, MelAuthServiceImpl melAuthService, MelNotification notification) {
        Lok.debug("FileSyncFXConflictSolverController.init");
        this.melFileSyncClientService = (MelFileSyncClientService) melAuthService.getMelService(notification.getServiceUuid());
        this.conflictDao = melFileSyncClientService.getFileSyncDatabaseManager().getConflictDao();
        ConflictSolver conflictSolver = this.melFileSyncClientService.getConflictSolverMap().get(notification.getSerializedExtra("c.id"));
//        this.conflictSolver = conflictSolver;
        this.stage.setTitle(notification.getTitle());
//        for (ConflictSolver conflictSolver : melFileSyncClientService.getConflictSolverMap().values()) {
        if (conflictSolver.hasConflicts() && !conflictSolver.isSolved()) {
            this.conflictSolver = conflictSolver;


//                TreeItem<Conflict> root = new TreeItem<>(new Conflict());
            TreeItem<Conflict> root = new TreeItem<>();
            treeTableView.setRoot(root);

//            colLocal.setCellValueFactory(new Callback<TreeTableColumn.CellDataFeatures<Conflict, Stage>, ObservableValue<Stage>>() {
//                @Override
//                public ObservableValue<Stage> call(TreeTableColumn.CellDataFeatures<Conflict, Stage> param) {
//                    return null;
//                }
//            });
            //colLeft.setCellValueFactory(new PropertyValueFactory<Conflict,Stage>("hurr"));
            //colLeft.setCellValueFactory(param -> param.getValue().getValue().getLeft());
//            colRemote.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().getValue().getKey()));
//            colLocal.setCellFactory(new Callback<TreeTableColumn<Conflict, Stage>, TreeTableCell<Conflict, Stage>>() {
//                @Override
//                public TreeTableCell<Conflict, Stage> call(TreeTableColumn<Conflict, Stage> param) {
//                    return new TreeConflictCell();
//                }
//            });
            colLocalName.setCellValueFactory(param -> param.getValue().getValue().getLocalStage().getName());
//            colLocalName.setCellValueFactory(param -> wrapString(N.ifNullElse(() -> param.getValue().getValue().getLocalStage().getName(), "--")));
            colLocalHash.setCellValueFactory(param -> wrapString(N.ifNullElse(() -> param.getValue().getValue().getLocalStage().getContentHash(), "--")));

            colRemoteName.setCellValueFactory(param -> wrapString(N.ifNullElse(() -> param.getValue().getValue().getRemoteStage().getName(), "--")));
            colRemoteHash.setCellValueFactory(param -> wrapString(N.ifNullElse(() -> param.getValue().getValue().getRemoteStage().getContentHash(), "--")));
//            colLocal.setCellValueFactory(param -> new TreeViewStage(param.getValue().getValue().getLocalStage()));

//                colLeft.setCellFactory(new Callback<TreeTableColumn<Conflict, String>, TreeTableCell<Conflict, String>>() {
//                    @Override
//                    public TreeTableCell<Conflict, String> call(TreeTableColumn<Conflict, String> param) {
//                        TreeConflictCell cell = new TreeConflictCell();
//                        return cell;
//                    }
//                });

            Lok.debug("debug 99fk,f");

            this.populateConflicts(root, conflictSolver.getRootConflictMap().values());


//                for (Conflict conflict : conflictSolver.getConflicts()) {
//                    root.getChildren().add(new TreeItem<>(conflict));
//                }


            //stop after the first one. we can only show one Activity anyway.
//                break;
//            }

            conflictSolver = (ConflictSolver) notification.getContent();

            Lok.debug("FileSyncFXConflictSolverController.init.done");
        }
    }

    private void populateConflicts(TreeItem<Conflict> root, Collection<Conflict> conflicts) {
        for (Conflict conflict : conflicts) {
            TreeItem<Conflict> treeItem = new TreeItem<>(conflict);
            populateConflicts(treeItem, conflict.getChildren());
            root.getChildren().add(treeItem);
            root.expandedProperty().setValue(true);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        colLocal.prefWidthProperty().bind(treeTableView.widthProperty().divide(3));
        colMerged.prefWidthProperty().bind(treeTableView.widthProperty().divide(3));
        colRemote.prefWidthProperty().bind(treeTableView.widthProperty().divide(3));
        treeTableView.setShowRoot(false);
    }
}
