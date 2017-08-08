package de.mein.drive.gui;

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
public class DriveFXConflictSolverControllerList extends PopupContentFX implements ConflictSolver.ConflictSolverListener{
    @FXML
    private ListView<Conflict> listLeft, listRight, listMerge;
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
            return "you bloody idiot! " + e.getClass().getSimpleName();

        }
    }

    @Override
    public void init(MeinService meinService, Object msgObject) {
        this.meinDriveClientService = (MeinDriveClientService) meinService;
        conflictSolver = (ConflictSolver) msgObject;
        meinDriveClientService.addConflictSolver(conflictSolver);
        System.out.println("DriveFXConflictSolverController.init");
        AbstractMergeListCell.setup(listLeft, listMerge, listRight);
        List<Conflict> conflicts = prepareConflicts(conflictSolver.getConflicts());
        listLeft.getItems().addAll(conflicts);
        conflictSolver.addListener(this);
    }

    /**
     * sorts, indents and adds empty rows
     *
     * @param conflicts
     * @return
     */
    private List<Conflict> prepareConflicts(Collection<Conflict> conflicts) {
        List<Conflict> result = new ArrayList<>();
        List<Conflict> rootConflicts = new ArrayList<>();
        for (Conflict conflict : conflicts) {
            if (conflict.getDependsOn() == null)
                rootConflicts.add(conflict);
        }
        for (Conflict root : rootConflicts) {
            result.add(root);
            traversalAdding(result, root.getDependents());
            if (root.getDependents().size() > 0)
                result.add(new EmptyRowConflict());
        }
        return result;
    }

    private void traversalAdding(List<Conflict> result, Set<Conflict> stuffToTraverse) {
        for (Conflict conflict : stuffToTraverse) {
            result.add(conflict);
            if (conflict.getDependents().size() > 0) {
                traversalAdding(result, conflict.getDependents());
            }
        }
    }

    @Override
    public void onConflictObsolete() {
        Platform.runLater(() -> stage.close());
    }

}
