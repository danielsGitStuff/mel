package de.mel.filesync.gui;

import de.mel.Lok;
import de.mel.auth.MelNotification;
import de.mel.auth.gui.PopupContentFX;
import de.mel.auth.service.MelAuthServiceImpl;
import de.mel.auth.tools.N;
import de.mel.filesync.data.conflict.Conflict;
import de.mel.filesync.data.conflict.ConflictRow;
import de.mel.filesync.data.conflict.ConflictSolver;
import de.mel.filesync.jobs.CommitJob;
import de.mel.filesync.service.MelFileSyncClientService;
import de.mel.filesync.sql.FsDirectory;
import de.mel.filesync.sql.FsEntry;
import de.mel.filesync.sql.Stage;
import de.mel.filesync.sql.dao.ConflictDao;
import de.mel.sql.SqlQueriesException;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;

import java.net.URL;
import java.util.*;


/**
 * Created by xor on 5/30/17.
 */
@SuppressWarnings("Duplicates")
public class FileSyncFXConflictSolverController extends PopupContentFX implements Initializable {
    @FXML
    private TreeTableView<ConflictRow> treeTableView;
    @FXML
    private TreeTableColumn<ConflictRow, String> colMerged, colMergedHash, colRemote, colRemoteName, colRemoteHash;
    @FXML
    private TreeTableColumn<ConflictRow, String> colLocal, colLocalHash;
    @FXML
    private TreeTableColumn<ConflictRow, ConflictRow> colDecideLocal, colDecideRemote, colMergedName;
    @FXML
    private TreeTableColumn<ConflictRow, String> colLocalName;

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
        this.stage.setTitle(notification.getTitle());
        if (conflictSolver.hasConflicts() && !conflictSolver.isSolved()) {
            this.conflictSolver = conflictSolver;


            colRemoteName.setCellValueFactory(param -> wrapString(param.getValue().getValue().getRemoteName()));
            colLocalName.setCellValueFactory(param -> wrapString(param.getValue().getValue().getLocalName()));
            colMergedName.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getValue()));
            colDecideLocal.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getValue()));
            colDecideRemote.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().getValue()));

            colDecideLocal.setCellFactory(param -> new MelFXButtonCell(">", true));
            colDecideRemote.setCellFactory(param -> new MelFXButtonCell("<", false));
            colLocalName.setCellFactory(param -> new MelFXTextCellNameLocal());
            colRemoteName.setCellFactory(param -> new MelFXTextCellRemote());
            colMergedName.setCellFactory(param -> new MelFXTextCellNameMerged());


            treeTableView.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);

            try {

                Map<String, ConflictRow> pathMap = this.populateConflicts(conflictSolver.getRootConflictMap().values());
                ConflictRow rootConflictRow = pathMap.get("");
                TreeItem<ConflictRow> root = new TreeItem<>();
                treeTableView.setRoot(root);
                this.dfsPopulate(root, rootConflictRow);
                root.expandedProperty().set(true);


            } catch (SqlQueriesException e) {
                e.printStackTrace();
            }
            Lok.debug("FileSyncFXConflictSolverController.init.done");
        }
    }


    /**
     * Connect trees from remote and local StageSets and {@link FsEntry}.
     * All conflicts will be linked together bottom up to the root {@link FsEntry} element.
     * {@link ConflictRow}s are kept in repo map. Key is their full path (meaning "path() + name()" ).
     * Root {@link FsEntry} is has the key "" then.
     *
     * @param repo
     * @param conflictRow
     * @throws SqlQueriesException
     */
    private void bottomUp(Map<String, ConflictRow> repo, ConflictRow conflictRow) throws SqlQueriesException {
        if (conflictRow.hasConflict() && conflictRow.getRemoteName() == "i0")
            Lok.debug("debug e0k,reer");
        if (conflictRow.hasFsEntry()) {
            FsEntry fsEntry = conflictRow.getFsEntry();
            if (fsEntry.getParentId().notNull()) {
                FsDirectory fsParent = this.conflictDao.getFsDao().getFsDirectoryById(fsEntry.getParentId().v());
                String parentPath = fsParent.getPath().v() + fsParent.getName().v();
                String path = fsEntry.getPath().v() + fsEntry.getName().v();
                repo.put(path, conflictRow);
                if (repo.containsKey(parentPath)) {
                    ConflictRow parentRow = repo.get(parentPath);
                    parentRow.addChild(conflictRow);
                } else {
                    ConflictRow parentRow = new ConflictRow(fsParent);
                    parentRow.addChild(conflictRow);
                    repo.put(parentPath, parentRow);
                    bottomUp(repo, conflictRow);
                }
            }
        } else if (conflictRow.hasConflict()) {
            Stage stage = null;
            if (conflictRow.getConflict().hasLocalStage())
                stage = conflictRow.getConflict().getLocalStage();
            else if (conflictRow.getConflict().hasRemoteStage())
                stage = conflictRow.getConflict().getRemoteStage();
            if (stage != null) {
                if (stage.getParentIdPair().notNull()) {
                    Stage parentStage = this.conflictDao.getStageDao().getStageById(stage.getParentId());
                    String parentPath = parentStage.getPath() + parentStage.getName();
                    String path = stage.getPath() + stage.getName();
                    repo.put(path, conflictRow);
                    if (repo.containsKey(parentPath)) {
                        ConflictRow parentRow = repo.get(parentPath);
                        parentRow.addChild(conflictRow);
                    } else {
                        ConflictRow parentRow = new ConflictRow(parentStage);
                        parentRow.addChild(conflictRow);
                        repo.put(parentPath, parentRow);
                        bottomUp(repo, parentRow);
                    }
                }
            }
            for (Conflict child : conflictRow.getConflict().getChildren().get()) {
                ConflictRow childRow = new ConflictRow(child);
                conflictRow.addChild(childRow);
                bottomUp(repo, childRow);
            }
        } else {
            Stage stage = conflictRow.getStage();
            if (stage.getParentIdPair().notNull()) {
                Stage parentStage = this.conflictDao.getStageDao().getStageById(stage.getParentId());
                String parentPath = parentStage.getPath() + parentStage.getName();
                ConflictRow parentRow;
                if (repo.containsKey(parentPath)) {
                    parentRow = repo.get(parentPath);
                } else {
                    parentRow = new ConflictRow(parentStage);
                }
                parentRow.addChild(conflictRow);
                repo.put(parentPath, parentRow);
            } else if (stage.getFsParentIdPair().notNull()) {
                FsDirectory fsParent = this.conflictDao.getFsDao().getFsDirectoryById(stage.getFsParentId());
                String parentPath = fsParent.getPath().v() + fsParent.getName().v();
                ConflictRow parentRow = new ConflictRow(fsParent);
                repo.put(parentPath, parentRow);
                bottomUp(repo, parentRow);
            }
        }
    }

    private TreeItem<ConflictRow> dfsPopulate(TreeItem<ConflictRow> parentTreeItem, ConflictRow currentRow) {
        TreeItem<ConflictRow> currentItem = new TreeItem<>(currentRow);
        for (ConflictRow child : currentRow.getChildren()) {
            dfsPopulate(currentItem, child);
        }
        parentTreeItem.getChildren().add(currentItem);
//        currentItem.expandedProperty().set(true);
        return currentItem;
    }

    private Map<String, ConflictRow> populateConflicts(Collection<Conflict> conflicts) throws SqlQueriesException {
        Map<String, ConflictRow> completePathConflictMap = new HashMap<>();
        for (Conflict conflict : conflicts) {
            String path = conflict.hasLocalStage() ? conflict.getLocalStage().getPath() + conflict.getLocalStage().getName() :
                    conflict.getRemoteStage().getPath() + conflict.getRemoteStage().getName();
            completePathConflictMap.put(path, new ConflictRow(conflict));
        }
        ConflictRow[] conflictRows = N.arr.fromCollection(completePathConflictMap.values(), ConflictRow.class);
        for (ConflictRow row : conflictRows) {
            bottomUp(completePathConflictMap, row);
        }
        return completePathConflictMap;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        colLocal.prefWidthProperty().bind(treeTableView.widthProperty().divide(3));
        colMerged.prefWidthProperty().bind(treeTableView.widthProperty().divide(3));
        colRemote.prefWidthProperty().bind(treeTableView.widthProperty().divide(3));
        treeTableView.setShowRoot(false);
    }
}
