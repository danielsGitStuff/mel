package de.mein.drive.gui;

import de.mein.Lok;
import de.mein.auth.MeinNotification;
import de.mein.auth.gui.PopupContentFX;
import de.mein.auth.gui.XCBFix;
import de.mein.auth.service.MeinAuthService;
import de.mein.drive.data.conflict.Conflict;
import de.mein.drive.data.conflict.ConflictSolver;
import de.mein.drive.jobs.CommitJob;
import de.mein.drive.service.MeinDriveClientService;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

import java.util.List;


/**
 * Created by xor on 5/30/17.
 */
public class DriveFXConflictSolverControllerList extends PopupContentFX implements ConflictSolver.ConflictSolverListener {
    @FXML
    private ListView<Conflict> listLeft, listRight, listMerge;
    private MeinDriveClientService meinDriveClientService;
    private ConflictSolver conflictSolver;


    @Override
    public String onOkCLicked() {
        Lok.debug("DriveFXConflictSolverController.onOkCLicked");
        if (conflictSolver.isSolved()) {
            conflictSolver.finished();
            CommitJob commitJob = new CommitJob();
            meinDriveClientService.addJob(commitJob);
        } else {
            System.err.println("not all conflicts were solved");
            meinDriveClientService.onConflicts();
        }
        return null;
    }

    @Override
    public void initImpl(Stage stage, MeinAuthService meinAuthService, MeinNotification notification) {
        this.meinDriveClientService = (MeinDriveClientService) meinAuthService.getMeinService(notification.getServiceUuid());
        conflictSolver = (ConflictSolver) notification.getContent();
        for (ConflictSolver conflictSolver : meinDriveClientService.getConflictSolverMap().values()) {
            this.conflictSolver = conflictSolver;
            Lok.debug("DriveFXConflictSolverController.init");
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
