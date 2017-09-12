package de.mein.drive.gui;

import de.mein.auth.MeinNotification;
import de.mein.auth.gui.PopupContentFX;
import de.mein.auth.service.MeinService;
import de.mein.drive.data.conflict.Conflict;
import de.mein.drive.data.conflict.ConflictException;
import de.mein.drive.data.conflict.ConflictSolver;
import de.mein.drive.data.conflict.EmptyRowConflict;
import de.mein.drive.jobs.CommitJob;
import de.mein.drive.service.MeinDriveClientService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;

import java.net.URL;
import java.util.*;


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
        System.out.println("DriveFXConflictSolverController.onOkCLicked");
        if (conflictSolver.isSolved()) {
            CommitJob commitJob = new CommitJob();
            meinDriveClientService.addJob(commitJob);
        }
        else {
            System.err.println("not all conflicts were solved");
        }
        return null;
    }

    @Override
    public void initImpl(MeinService meinService, MeinNotification notification) {
        this.meinDriveClientService = (MeinDriveClientService) meinService;
        conflictSolver = (ConflictSolver) notification.getContent();
        System.out.println("DriveFXConflictSolverController.init");
        AbstractMergeListCell.setup(listLeft, listMerge, listRight);
        List<Conflict> conflicts = Conflict.prepareConflicts(conflictSolver.getConflicts());
        listLeft.getItems().addAll(conflicts);
        conflictSolver.addListener(this);
    }


    @Override
    public void onConflictObsolete() {
        Platform.runLater(() -> stage.close());
    }

}
