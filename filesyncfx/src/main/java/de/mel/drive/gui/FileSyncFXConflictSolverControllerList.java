package de.mel.drive.gui;

import de.mel.Lok;
import de.mel.auth.MelNotification;
import de.mel.auth.gui.PopupContentFX;
import de.mel.auth.gui.XCBFix;
import de.mel.auth.service.MelAuthService;
import de.mel.drive.data.conflict.Conflict;
import de.mel.drive.data.conflict.ConflictSolver;
import de.mel.drive.jobs.CommitJob;
import de.mel.drive.service.MelFileSyncClientService;
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
        for (ConflictSolver conflictSolver : melFileSyncClientService.getConflictSolverMap().values()) {
            this.conflictSolver = conflictSolver;
            Lok.debug("FileSyncFXConflictSolverController.init");
            AbstractMergeListCell.setup(listLeft, listMerge, listRight);
            List<Conflict> conflicts = Conflict.prepareConflicts(conflictSolver.getConflicts());
            listLeft.getItems().addAll(conflicts);
            conflictSolver.addListener(this);
            break;
        }

    }


    @Override
    public void onConflictObsolete() {
        XCBFix.runLater(() -> stage.close());
    }

}
