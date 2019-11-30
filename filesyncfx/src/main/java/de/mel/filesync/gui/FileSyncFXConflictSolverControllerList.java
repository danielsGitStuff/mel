package de.mel.filesync.gui;

import de.mel.Lok;
import de.mel.auth.MelNotification;
import de.mel.auth.gui.PopupContentFX;
import de.mel.auth.gui.XCBFix;
import de.mel.auth.service.MelAuthService;
import de.mel.filesync.data.conflict.Conflict;
import de.mel.filesync.data.conflict.ConflictSolver;
import de.mel.filesync.jobs.CommitJob;
import de.mel.filesync.service.MelFileSyncClientService;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

import java.util.List;


/**
 * Created by xor on 5/30/17.
 */
public class FileSyncFXConflictSolverControllerList extends PopupContentFX implements ConflictSolver.ConflictSolverListener {
    @FXML
    private ListView<Conflict> listLeft, listRight, listMerge;
    private MelFileSyncClientService melFileSyncClientService;
    private ConflictSolver conflictSolver;


    @Override
    public String onOkCLicked() {
        Lok.debug("FileSyncFXConflictSolverController.onOkCLicked");
        if (conflictSolver.isSolved()) {
            conflictSolver.probablyFinished();
            CommitJob commitJob = new CommitJob();
            melFileSyncClientService.addJob(commitJob);
        } else {
            System.err.println("not all conflicts were solved");
            melFileSyncClientService.onConflicts();
        }
        return null;
    }

    @Override
    public void initImpl(Stage stage, MelAuthService melAuthService, MelNotification notification) {
        this.melFileSyncClientService = (MelFileSyncClientService) melAuthService.getMelService(notification.getServiceUuid());
        conflictSolver = (ConflictSolver) notification.getContent();
        ConflictSolver conflictSolver = melFileSyncClientService.getConflictSolverMap().values().iterator().next();
        this.conflictSolver = conflictSolver;
        Lok.debug("FileSyncFXConflictSolverController.init");
        AbstractMergeListCell.setup(listLeft, listMerge, listRight);
        List<Conflict> conflicts = Conflict.prepareConflicts(conflictSolver.getConflicts());
        listLeft.getItems().addAll(conflicts);
        conflictSolver.addListener(this);
    }


    @Override
    public void onConflictObsolete() {
        XCBFix.runLater(() -> stage.close());
    }

}
